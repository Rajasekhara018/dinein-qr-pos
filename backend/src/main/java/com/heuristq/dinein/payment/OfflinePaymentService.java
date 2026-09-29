package com.heuristq.dinein.payment;

import com.heuristq.dinein.order.domain.OrderEntity;
import com.heuristq.dinein.order.domain.OrderRepository;
import com.heuristq.dinein.order.domain.OrderStatus;
import com.heuristq.dinein.payment.PaymentStateService.CaptureOutcome;
import com.heuristq.dinein.payment.domain.OfflinePaymentMethod;
import com.heuristq.dinein.payment.domain.PaymentEntity;
import com.heuristq.dinein.payment.domain.PaymentRepository;
import com.heuristq.dinein.payment.domain.PaymentStatus;
import com.heuristq.dinein.shared.exception.ApiException;
import com.heuristq.dinein.shared.util.Money;
import com.heuristq.dinein.shared.util.SecureTokens;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Set;

/**
 * Records money taken by staff (cash, UPI or card at the counter) as a payment with provider {@code OFFLINE} and
 * confirms the order through {@link PaymentStateService#confirmCapture}, the same path online captures use. Kitchen
 * realtime, notifications and the flag rules therefore behave exactly as for an online payment. No gateway is ever
 * involved: OFFLINE is not a registered {@code PaymentGateway}.
 */
@Slf4j
@Service
public class OfflinePaymentService {

    private static final Set<OrderStatus> SETTLEABLE =
            EnumSet.of(OrderStatus.PENDING_PAYMENT, OrderStatus.EXPIRED, OrderStatus.PAYMENT_FAILED);

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentStateService paymentStateService;

    public OfflinePaymentService(OrderRepository orderRepository, PaymentRepository paymentRepository,
                                 PaymentStateService paymentStateService) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.paymentStateService = paymentStateService;
    }

    /**
     * Settles an unpaid order (PENDING_PAYMENT, EXPIRED or PAYMENT_FAILED) with an offline payment of the full grand
     * total and confirms it. The order row is locked first, so a concurrent online capture either happened before
     * (409 ALREADY_PAID) or sees this payment afterwards and flags the order as paid twice.
     *
     * @throws ApiException 409 ORDER_NOT_PAYABLE, ALREADY_PAID or PAYMENT_FLAGGED
     */
    @Transactional
    public void recordAndConfirm(Long orderId, OfflinePaymentMethod method, Long staffUserId, String actor) {
        OrderEntity order = orderRepository.findByIdForUpdate(orderId).orElseThrow(() -> ApiException.notFound("Order"));
        if (order.isPaymentFlagged()) {
            throw ApiException.conflict("PAYMENT_FLAGGED",
                    "This order's payment needs review. Resolve the flag before settling it at the counter.");
        }
        boolean alreadyPaid = paymentRepository.findByOrderIdOrderByIdAsc(orderId).stream()
                .anyMatch(p -> p.getStatus() == PaymentStatus.CAPTURED || p.getStatus() == PaymentStatus.REFUNDED);
        if (alreadyPaid) {
            throw ApiException.conflict("ALREADY_PAID", "This order has already been paid");
        }
        if (!SETTLEABLE.contains(order.getStatus())) {
            throw ApiException.conflict("ORDER_NOT_PAYABLE",
                    "This order can no longer be paid (" + order.getStatus() + ")");
        }
        long amountPaise = Money.toPaise(order.getGrandTotal());
        PaymentEntity payment = new PaymentEntity();
        payment.setRestaurantId(order.getRestaurantId());
        payment.setOrderId(orderId);
        payment.setProvider(PaymentEntity.OFFLINE_PROVIDER);
        payment.setProviderOrderId("off_" + orderId + "_" + SecureTokens.randomUrlSafe(9));
        payment.setAmountPaise(amountPaise);
        payment.setCurrency("INR");
        payment.setStatus(PaymentStatus.CREATED);
        payment.setMethod(method.name());
        payment.setRecordedByStaffId(staffUserId);
        paymentRepository.saveAndFlush(payment);

        CaptureOutcome outcome = paymentStateService.confirmCapture(PaymentEntity.OFFLINE_PROVIDER,
                payment.getProviderOrderId(), null, amountPaise, method.name(), actor);
        if (outcome != CaptureOutcome.CONFIRMED) {
            // Cannot happen under the lock taken above; roll everything back rather than leave a half-paid order.
            throw new IllegalStateException("Offline payment for order " + orderId + " was not confirmed: " + outcome);
        }
        log.info("payment.offline.recorded orderId={} method={} amountPaise={} actor={}",
                orderId, method, amountPaise, actor);
    }
}
