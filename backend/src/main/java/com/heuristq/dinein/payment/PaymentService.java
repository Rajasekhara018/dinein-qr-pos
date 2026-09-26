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
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
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
                publicBaseUrl + "/api/public/payments/" + gateway.code().toLowerCase() + "/callback",
                publicBaseUrl + "/menu/orders/" + order.getId());
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
        PaymentEntity payment = paymentRepository.findByProviderAndProviderOrderId(gateway.code(), verified.providerOrderId())
                .orElseThrow(() -> ApiException.notFound("Payment"));
        OrderEntity order = orderRepository.findById(payment.getOrderId())
                .filter(o -> guest == null || o.getGuestSessionId().equals(guest.sessionId()))
                .orElseThrow(() -> ApiException.notFound("Order"));

        ProviderPayment authoritative = gateway.fetchPayment(verified.providerOrderId(), verified.providerPaymentId());
        if (authoritative.providerOrderId() != null && !verified.providerOrderId().equals(authoritative.providerOrderId())) {
            log.warn("payment.verify.order_mismatch orderId={} provider={}", order.getId(), provider);
            throw ApiException.badRequest("PAYMENT_MISMATCH", "Payment does not belong to this order");
        }
        paymentStateService.apply(gateway.code(), authoritative, guest == null ? "redirect" : "client-verify");
        return order.getId();
    }

    /** Guard used by public endpoints: the order must belong to the caller's guest session. */
    @Transactional(readOnly = true)
    public void assertOwnedBy(Long orderId, GuestSession guest) {
        orderRepository.findById(orderId)
                .filter(o -> o.getGuestSessionId().equals(guest.sessionId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Order not found"));
    }

    public String activeProvider() {
        return gateways.active().code();
    }
}
