package com.heuristq.dinein.payment;

import com.heuristq.dinein.order.OrderLifecycleService;
import com.heuristq.dinein.order.domain.OrderEntity;
import com.heuristq.dinein.order.domain.OrderRepository;
import com.heuristq.dinein.order.domain.OrderStatus;
import com.heuristq.dinein.payment.domain.PaymentEntity;
import com.heuristq.dinein.payment.domain.PaymentRepository;
import com.heuristq.dinein.payment.domain.PaymentStatus;
import com.heuristq.dinein.payment.domain.RefundStatus;
import com.heuristq.dinein.payment.gateway.ProviderPayment;
import com.heuristq.dinein.shared.util.Money;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * All payment-driven state changes, independent of the gateway. Client verify, redirects, webhooks and the expiry job
 * can arrive in any order and more than once, so every method locks the order row ({@code SELECT ... FOR UPDATE})
 * and is idempotent. An order is confirmed only from PENDING_PAYMENT (or a late capture after expiry/failure), only
 * once, and only when the captured amount equals its grand total.
 */
@Slf4j
@Service
public class PaymentStateService {

    public enum CaptureOutcome { CONFIRMED, ALREADY_CAPTURED, FLAGGED, UNKNOWN_PAYMENT, DUPLICATE_PAYMENT }

    private static final Set<OrderStatus> CONFIRMABLE =
            EnumSet.of(OrderStatus.PENDING_PAYMENT, OrderStatus.EXPIRED, OrderStatus.PAYMENT_FAILED);

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final OrderLifecycleService lifecycle;
    private final ApplicationEventPublisher events;

    public PaymentStateService(PaymentRepository paymentRepository, OrderRepository orderRepository,
                               OrderLifecycleService lifecycle, ApplicationEventPublisher events) {
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.lifecycle = lifecycle;
        this.events = events;
    }

    /** Applies any provider-reported payment outcome. */
    @Transactional
    public void apply(String provider, ProviderPayment payment, String source) {
        switch (payment.outcome()) {
            case CAPTURED -> confirmCapture(provider, payment.providerOrderId(), payment.providerPaymentId(),
                    payment.amountPaise(), payment.method(), source);
            case AUTHORIZED -> markAuthorized(provider, payment.providerOrderId(), payment.providerPaymentId(), payment.method());
            case FAILED -> recordFailure(provider, payment.providerOrderId(), payment.providerPaymentId(), payment.errorDescription());
            case PENDING -> log.info("payment.pending provider={} providerOrderId={} source={}", provider,
                    payment.providerOrderId(), source);
        }
    }

    @Transactional
    public CaptureOutcome confirmCapture(String provider, String providerOrderId, String providerPaymentId,
                                         long capturedPaise, String method, String source) {
        Optional<PaymentEntity> found = paymentRepository.findByProviderAndProviderOrderId(provider, providerOrderId);
        if (found.isEmpty()) {
            log.warn("payment.capture.unknown provider={} providerOrderId={} source={}", provider, providerOrderId, source);
            return CaptureOutcome.UNKNOWN_PAYMENT;
        }
        // Lock the order first, then re-read the payment under that lock.
        OrderEntity order = orderRepository.findByIdForUpdate(found.get().getOrderId()).orElseThrow();
        PaymentEntity payment = paymentRepository.findById(found.get().getId()).orElseThrow();

        if (payment.getStatus() == PaymentStatus.CAPTURED || payment.getStatus() == PaymentStatus.REFUNDED) {
            if (providerPaymentId != null && providerPaymentId.equals(payment.getProviderPaymentId())) {
                log.info("payment.capture.duplicate orderId={} source={}", order.getId(), source);
                return CaptureOutcome.ALREADY_CAPTURED;
            }
            flag(order, "Second payment " + providerPaymentId + " captured; refund it manually from the " + provider + " dashboard");
            log.error("payment.capture.second_payment orderId={} existing={} new={}",
                    order.getId(), payment.getProviderPaymentId(), providerPaymentId);
            return CaptureOutcome.DUPLICATE_PAYMENT;
        }
        // Another attempt row of the same order (PayU retries) may already have been captured.
        boolean otherCaptured = paymentRepository.findByOrderIdOrderByIdAsc(order.getId()).stream()
                .anyMatch(p -> !p.getId().equals(payment.getId()) && p.getStatus() == PaymentStatus.CAPTURED);

        payment.setStatus(PaymentStatus.CAPTURED);
        payment.setProviderPaymentId(providerPaymentId);
        payment.setCapturedAmountPaise(capturedPaise);
        payment.setFailureReason(null);
        if (method != null) {
            payment.setMethod(method.length() > 20 ? method.substring(0, 20) : method);
        }
        paymentRepository.save(payment);

        if (otherCaptured) {
            flag(order, "Order paid twice (" + providerPaymentId + "); refund one payment manually");
            log.error("payment.capture.second_attempt_captured orderId={}", order.getId());
            return CaptureOutcome.DUPLICATE_PAYMENT;
        }
        long expected = Money.toPaise(order.getGrandTotal());
        if (capturedPaise != expected || payment.getAmountPaise() != expected) {
            flag(order, "Captured " + capturedPaise + " paise but order total is " + expected + " paise");
            log.error("payment.capture.amount_mismatch orderId={} captured={} expected={} source={}",
                    order.getId(), capturedPaise, expected, source);
            return CaptureOutcome.FLAGGED;
        }
        if (CONFIRMABLE.contains(order.getStatus())) {
            lifecycle.transition(order, OrderStatus.CONFIRMED, "payment:" + provider + ":" + source);
            log.info("payment.captured orderId={} provider={} method={} source={}",
                    order.getId(), provider, payment.getMethod(), source);
            return CaptureOutcome.CONFIRMED;
        }
        log.info("payment.capture.order_already status={} orderId={} source={}", order.getStatus(), order.getId(), source);
        return CaptureOutcome.ALREADY_CAPTURED;
    }

    @Transactional
    public void markAuthorized(String provider, String providerOrderId, String providerPaymentId, String method) {
        paymentRepository.findByProviderAndProviderOrderId(provider, providerOrderId).ifPresent(p -> {
            orderRepository.findByIdForUpdate(p.getOrderId());
            PaymentEntity payment = paymentRepository.findById(p.getId()).orElseThrow();
            if (payment.getStatus() == PaymentStatus.CREATED || payment.getStatus() == PaymentStatus.FAILED) {
                payment.setStatus(PaymentStatus.AUTHORIZED);
                payment.setProviderPaymentId(providerPaymentId);
                payment.setMethod(method);
            }
        });
    }

    /**
     * A failed attempt. The order stays PENDING_PAYMENT so the guest can retry; the expiry job later turns it into
     * PAYMENT_FAILED if nothing succeeds.
     */
    @Transactional
    public void recordFailure(String provider, String providerOrderId, String providerPaymentId, String reason) {
        paymentRepository.findByProviderAndProviderOrderId(provider, providerOrderId).ifPresentOrElse(p -> {
            orderRepository.findByIdForUpdate(p.getOrderId());
            PaymentEntity payment = paymentRepository.findById(p.getId()).orElseThrow();
            if (payment.getStatus() == PaymentStatus.CAPTURED || payment.getStatus() == PaymentStatus.REFUNDED) {
                return;
            }
            payment.setStatus(PaymentStatus.FAILED);
            String r = reason == null || reason.isBlank() ? "Payment failed" : reason;
            payment.setFailureReason(r.length() > 300 ? r.substring(0, 300) : r);
            log.info("payment.failed orderId={} provider={} reason={}", payment.getOrderId(), provider, payment.getFailureReason());
        }, () -> log.warn("payment.failed.unknown provider={} providerOrderId={}", provider, providerOrderId));
    }

    /** Expires an unpaid order (called by the expiry job after it has reconciled with the provider). */
    @Transactional
    public boolean expire(Long orderId, OrderStatus terminal) {
        OrderEntity order = orderRepository.findByIdForUpdate(orderId).orElse(null);
        if (order == null || order.getStatus() != OrderStatus.PENDING_PAYMENT || order.isPaymentFlagged()) {
            return false;
        }
        lifecycle.transition(order, terminal, "expiry-job");
        return true;
    }

    @Transactional
    public void markRefundProcessed(String provider, String providerPaymentId, String refundId) {
        paymentRepository.findByProviderAndProviderPaymentId(provider, providerPaymentId).ifPresentOrElse(p -> {
            p.setRefundStatus(RefundStatus.PROCESSED);
            p.setStatus(PaymentStatus.REFUNDED);
            if (refundId != null) {
                p.setProviderRefundId(refundId);
            }
            log.info("payment.refund.processed orderId={} refundId={}", p.getOrderId(), refundId);
        }, () -> log.warn("payment.refund.unknown_payment provider={} paymentId={}", provider, providerPaymentId));
    }

    @Transactional
    public void markRefundFailed(String provider, String providerPaymentId, String refundId) {
        paymentRepository.findByProviderAndProviderPaymentId(provider, providerPaymentId).ifPresent(p -> {
            if (p.getRefundStatus() != RefundStatus.PROCESSED) {
                p.setRefundStatus(RefundStatus.FAILED);
                log.error("payment.refund.failed orderId={} refundId={}", p.getOrderId(), refundId);
                events.publishEvent(new RefundFailedEvent(p.getOrderId(), provider));
            }
        });
    }

    private void flag(OrderEntity order, String reason) {
        order.setPaymentFlagged(true);
        order.setFlagReason(reason.length() > 300 ? reason.substring(0, 300) : reason);
        orderRepository.save(order);
        events.publishEvent(new PaymentFlaggedEvent(order.getId(), order.getFlagReason()));
    }
}
