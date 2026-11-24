package com.heuristq.dinein.payment;

import com.heuristq.dinein.audit.AuditService;
import com.heuristq.dinein.order.OrderLifecycleService;
import com.heuristq.dinein.order.domain.OrderEntity;
import com.heuristq.dinein.order.domain.OrderRepository;
import com.heuristq.dinein.order.domain.OrderStatus;
import com.heuristq.dinein.payment.domain.PaymentEntity;
import com.heuristq.dinein.payment.domain.PaymentRepository;
import com.heuristq.dinein.payment.domain.PaymentStatus;
import com.heuristq.dinein.payment.domain.RefundStatus;
import com.heuristq.dinein.payment.infrastructure.gateway.PaymentGatewayRegistry;
import com.heuristq.dinein.payment.infrastructure.gateway.ProviderRefund;
import com.heuristq.dinein.shared.exception.ApiException;
import com.heuristq.dinein.shared.util.Text;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.EnumSet;
import java.util.Set;

/**
 * Admin cancellation with a full refund through the gateway that took the payment. Cancelling is committed first;
 * the refund call follows. If the provider call fails, calling cancel again retries only the refund. Offline payments
 * (provider OFFLINE) are never sent to a gateway: their refund is marked {@link RefundStatus#MANUAL} (cash back).
 */
@Slf4j
@Service
public class RefundService {

    private static final Set<OrderStatus> CANCELLABLE = EnumSet.of(OrderStatus.CONFIRMED, OrderStatus.PREPARING);

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final OrderLifecycleService lifecycle;
    private final PaymentGatewayRegistry gateways;
    private final TransactionTemplate tx;
    private final ApplicationEventPublisher events;
    private final AuditService auditService;

    public RefundService(OrderRepository orderRepository, PaymentRepository paymentRepository,
                         OrderLifecycleService lifecycle, PaymentGatewayRegistry gateways, TransactionTemplate tx,
                         ApplicationEventPublisher events, AuditService auditService) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.lifecycle = lifecycle;
        this.gateways = gateways;
        this.tx = tx;
        this.events = events;
        this.auditService = auditService;
    }

    public void cancelAndRefund(Long orderId, String reason, String actor) {
        PaymentEntity toRefund = tx.execute(s -> {
            OrderEntity order = orderRepository.findByIdForUpdate(orderId).orElseThrow(() -> ApiException.notFound("Order"));
            if (order.getStatus() != OrderStatus.CANCELLED) {
                if (!CANCELLABLE.contains(order.getStatus())) {
                    throw ApiException.conflict("ILLEGAL_TRANSITION",
                            "Only paid orders that are not yet ready can be cancelled (current: " + order.getStatus() + ")");
                }
                OrderStatus previousStatus = order.getStatus();
                order.setCancelReason(Text.clean(reason, 300));
                lifecycle.transition(order, OrderStatus.CANCELLED, actor);
                auditService.record("ORDER_CANCELLED", "Order", order.getId(), previousStatus.name(), reason);
            }
            PaymentEntity captured = paymentRepository.findByOrderIdOrderByIdAsc(orderId).stream()
                    .filter(p -> p.getStatus() == PaymentStatus.CAPTURED)
                    .filter(p -> p.getRefundStatus() == null || p.getRefundStatus() == RefundStatus.FAILED)
                    .findFirst().orElse(null);
            if (captured != null) {
                // Offline (counter) money is handed back by staff: record that, never call a gateway.
                captured.setRefundStatus(captured.isOffline() ? RefundStatus.MANUAL : RefundStatus.PENDING);
            }
            return captured;
        });
        if (toRefund == null) {
            log.info("order.cancel.no_refund_needed orderId={}", orderId);
            return;
        }
        if (toRefund.isOffline()) {
            log.info("order.refund.manual orderId={} method={} amountPaise={}", orderId, toRefund.getMethod(),
                    toRefund.getAmountPaise());
            auditService.record("REFUND_INITIATED", "Order", orderId, null, "manual (offline payment)");
            return;
        }
        try {
            ProviderRefund refund = gateways.get(toRefund.getProvider()).refund(toRefund.getProviderOrderId(),
                    toRefund.getProviderPaymentId(), toRefund.getCapturedAmountPaise() != null
                            ? toRefund.getCapturedAmountPaise() : toRefund.getAmountPaise(),
                    "refund-" + orderId);
            tx.executeWithoutResult(s -> paymentRepository.findById(toRefund.getId()).ifPresent(p -> {
                p.setProviderRefundId(refund.refundId());
                if (refund.processed()) {
                    p.setRefundStatus(RefundStatus.PROCESSED);
                    p.setStatus(PaymentStatus.REFUNDED);
                }
            }));
            log.info("order.refund.requested orderId={} provider={} refundId={}", orderId, toRefund.getProvider(), refund.refundId());
            auditService.record("REFUND_INITIATED", "Order", orderId, null, toRefund.getProvider() + ":" + refund.refundId());
        } catch (ApiException e) {
            tx.executeWithoutResult(s -> paymentRepository.findById(toRefund.getId())
                    .ifPresent(p -> p.setRefundStatus(RefundStatus.FAILED)));
            events.publishEvent(new RefundFailedEvent(orderId, toRefund.getProvider()));
            throw new ApiException(HttpStatus.BAD_GATEWAY, "REFUND_FAILED",
                    "Order was cancelled but the refund could not be started. Try cancelling again to retry the refund.");
        }
    }
}
