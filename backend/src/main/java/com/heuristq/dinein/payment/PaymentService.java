package com.heuristq.dinein.payment;

import com.heuristq.dinein.guest.GuestSession;
import com.heuristq.dinein.order.domain.OrderEntity;
import com.heuristq.dinein.order.domain.OrderRepository;
import com.heuristq.dinein.order.domain.OrderStatus;
import com.heuristq.dinein.payment.domain.PaymentEntity;
import com.heuristq.dinein.payment.domain.PaymentRepository;
import com.heuristq.dinein.payment.domain.PaymentStatus;
import com.heuristq.dinein.payment.dto.PaymentDtos.CheckoutResponse;
import com.heuristq.dinein.payment.gateway.CheckoutContext;
import com.heuristq.dinein.payment.gateway.CheckoutPayload;
import com.heuristq.dinein.payment.gateway.ClientVerification;
import com.heuristq.dinein.payment.gateway.PaymentGateway;
import com.heuristq.dinein.payment.gateway.PaymentGatewayRegistry;
import com.heuristq.dinein.payment.gateway.ProviderPayment;
import com.heuristq.dinein.settings.SettingsService;
import com.heuristq.dinein.settings.domain.RestaurantSettingsEntity;
import com.heuristq.dinein.shared.config.AppProperties;
import com.heuristq.dinein.shared.exception.ApiException;
import com.heuristq.dinein.shared.util.Money;
import com.heuristq.dinein.shared.web.ApiPaths;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Orchestrates checkout and client-side verification through whichever {@link PaymentGateway} is in use. */
@Slf4j
@Service
public class PaymentService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentStateService paymentStateService;
    private final PaymentGatewayRegistry gateways;
    private final SettingsService settingsService;
    private final String publicBaseUrl;

    public PaymentService(OrderRepository orderRepository, PaymentRepository paymentRepository,
                          PaymentStateService paymentStateService, PaymentGatewayRegistry gateways,
                          SettingsService settingsService, AppProperties properties) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.paymentStateService = paymentStateService;
        this.gateways = gateways;
        this.settingsService = settingsService;
        String base = properties.publicBaseUrl();
        this.publicBaseUrl = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    /**
     * Returns checkout details, creating the provider order on first use. Retries reuse the open provider order when
     * the gateway allows it; otherwise a new attempt row is created. The order row is locked meanwhile, so concurrent
     * retries cannot create two provider orders.
     *
     * @param strict when true a non-payable order is a 409; otherwise its current status is returned
     */
    @Transactional
    public CheckoutResponse ensureCheckout(Long orderId, boolean strict) {
        OrderEntity order = orderRepository.findByIdForUpdate(orderId).orElseThrow(() -> ApiException.notFound("Order"));
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT || order.isPaymentFlagged()) {
            if (strict) {
                throw ApiException.conflict("ORDER_NOT_PAYABLE", "This order can no longer be paid (" + order.getStatus() + ")");
            }
            return new CheckoutResponse(order.getId(), order.getOrderNumber(), order.getDisplayToken(), order.getStatus(),
                    null, null, null, null, null, null);
        }
        RestaurantSettingsEntity settings = settingsService.current();
        Optional<PaymentEntity> open = paymentRepository.findByOrderIdOrderByIdAsc(orderId).stream()
                .filter(p -> !p.isOffline())
                .filter(p -> p.getStatus() == PaymentStatus.CREATED || p.getStatus() == PaymentStatus.AUTHORIZED
                        || (p.getStatus() == PaymentStatus.FAILED && gateways.get(p.getProvider()).reusableAfterFailure()))
                .max(Comparator.comparing(PaymentEntity::getId));

        PaymentGateway gateway = open.map(p -> gateways.get(p.getProvider())).orElseGet(gateways::active);
        CheckoutContext context = contextFor(order, settings, gateway);
        PaymentEntity payment = open.orElseGet(() -> createAttempt(order, gateway, context));
        CheckoutPayload payload = gateway.checkoutPayload(context.withProviderOrderId(payment.getProviderOrderId()));
        return new CheckoutResponse(order.getId(), order.getOrderNumber(), order.getDisplayToken(), order.getStatus(),
                gateway.code(), payload.mode(), payload.data(), payment.getAmountPaise(), payment.getCurrency(),
                settings.getName());
    }

    private CheckoutContext contextFor(OrderEntity order, RestaurantSettingsEntity settings, PaymentGateway gateway) {
        return new CheckoutContext(order.getId(), order.getOrderNumber(), Money.toPaise(order.getGrandTotal()), "INR",
                order.getCustomerName(), order.getCustomerPhone(), settings.getName(), settings.getBrandColor(), null,
                publicBaseUrl + ApiPaths.V1 + "/public/payments/" + gateway.code().toLowerCase() + "/callback",
                publicBaseUrl + orderPagePath(order));
    }

    /**
     * Frontend page for an order after payment: the guest's order page, or the waiter screen's order page for
     * staff-assisted orders (those have no guest session, so the guest page could not show them).
     */
    public static String orderPagePath(OrderEntity order) {
        return (order.isPlacedByStaff() ? "/waiter/orders/" : "/menu/orders/") + order.getId();
    }

    /** {@link #orderPagePath(OrderEntity)} by id; falls back to the guest page when the order is unknown. */
    @Transactional(readOnly = true)
    public String orderPagePath(Long orderId) {
        return orderRepository.findById(orderId).map(PaymentService::orderPagePath).orElse("/menu/orders/" + orderId);
    }

    /**
     * Response for an order settled offline: same shape as a checkout, with {@code provider = OFFLINE}, the amount,
     * and null {@code mode}/{@code checkout} (nothing to pay). Empty when the order has no captured offline payment.
     */
    @Transactional(readOnly = true)
    public Optional<CheckoutResponse> offlineReceipt(Long orderId) {
        OrderEntity order = orderRepository.findById(orderId).orElseThrow(() -> ApiException.notFound("Order"));
        return paymentRepository.findByOrderIdOrderByIdAsc(orderId).stream()
                .filter(p -> p.isOffline() && p.getStatus() == PaymentStatus.CAPTURED)
                .findFirst()
                .map(p -> new CheckoutResponse(order.getId(), order.getOrderNumber(), order.getDisplayToken(),
                        order.getStatus(), PaymentEntity.OFFLINE_PROVIDER, null, null, p.getAmountPaise(),
                        p.getCurrency(), settingsService.current().getName()));
    }

    /** Whether online payment can be offered right now (the active gateway is configured). */
    public boolean onlinePaymentsAvailable() {
        try {
            gateways.active();
            return true;
        } catch (ApiException e) {
            return false;
        }
    }

    private PaymentEntity createAttempt(OrderEntity order, PaymentGateway gateway, CheckoutContext context) {
        String providerOrderId = gateway.createProviderOrder(context);
        PaymentEntity payment = new PaymentEntity();
        payment.setOrderId(order.getId());
        payment.setProvider(gateway.code());
        payment.setProviderOrderId(providerOrderId);
        payment.setAmountPaise(context.amountPaise());
        payment.setCurrency(context.currency());
        payment.setStatus(PaymentStatus.CREATED);
        paymentRepository.save(payment);
        log.info("payment.created orderId={} provider={} providerOrderId={} amountPaise={}",
                order.getId(), gateway.code(), providerOrderId, context.amountPaise());
        return payment;
    }

    /**
     * Verifies what the browser (SDK success handler) or a provider redirect sends back. The signature/hash proves the
     * ids came from the provider; the payment is then read from the provider's API so status and amount are
     * authoritative before the order is confirmed.
     *
     * @param guest null for provider redirects (cross-site POST without our cookie); the hash authenticates those
     * @return the order id
     */
    public Long verifyCallback(String provider, Map<String, String> params, GuestSession guest) {
        PaymentGateway gateway = gateways.get(provider);
        ClientVerification verified = gateway.verifyClientCallback(params);
        PaymentEntity payment = (verified.providerOrderId() != null
                ? paymentRepository.findByProviderAndProviderOrderId(gateway.code(), verified.providerOrderId())
                : latestPaymentFor(verified.internalOrderId(), gateway.code()))
                .orElseThrow(() -> ApiException.notFound("Payment"));
        if (verified.providerOrderId() == null) {
            verified = new ClientVerification(payment.getProviderOrderId(), null);
        }
        OrderEntity order = orderRepository.findById(payment.getOrderId())
                .filter(o -> guest == null || o.belongsToGuest(guest.sessionId()))
                .orElseThrow(() -> ApiException.notFound("Order"));

        ProviderPayment authoritative = gateway.fetchPayment(verified.providerOrderId(), verified.providerPaymentId());
        if (authoritative.providerOrderId() != null && !verified.providerOrderId().equals(authoritative.providerOrderId())) {
            log.warn("payment.verify.order_mismatch orderId={} provider={}", order.getId(), provider);
            throw ApiException.badRequest("PAYMENT_MISMATCH", "Payment does not belong to this order");
        }
        paymentStateService.apply(gateway.code(), authoritative, guest == null ? "redirect" : "client-verify");
        return order.getId();
    }

    private Optional<PaymentEntity> latestPaymentFor(Long orderId, String provider) {
        if (orderId == null) {
            return Optional.empty();
        }
        List<PaymentEntity> payments = paymentRepository.findByOrderIdOrderByIdAsc(orderId);
        for (int i = payments.size() - 1; i >= 0; i--) {
            if (provider.equals(payments.get(i).getProvider())) {
                return Optional.of(payments.get(i));
            }
        }
        return Optional.empty();
    }

    /** Guard used by public endpoints: the order must belong to the caller's guest session. */
    @Transactional(readOnly = true)
    public void assertOwnedBy(Long orderId, GuestSession guest) {
        orderRepository.findById(orderId)
                .filter(o -> o.belongsToGuest(guest.sessionId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Order not found"));
    }

    /** Guard for staff payment endpoints: only staff-assisted orders are paid from the waiter screen. */
    @Transactional(readOnly = true)
    public void assertPlacedByStaff(Long orderId) {
        OrderEntity order = orderRepository.findById(orderId).orElseThrow(() -> ApiException.notFound("Order"));
        if (!order.isPlacedByStaff()) {
            throw ApiException.conflict("NOT_STAFF_ORDER", "This order was placed by the guest; they pay for it on their phone");
        }
    }

    public String activeProvider() {
        return gateways.active().code();
    }
}
