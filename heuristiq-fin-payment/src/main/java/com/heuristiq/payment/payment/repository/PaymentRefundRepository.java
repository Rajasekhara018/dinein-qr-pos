package com.heuristiq.paymentservice.payment.repository;

import com.heuristiq.paymentservice.payment.model.PaymentRefund;
import com.heuristiq.paymentservice.payment.model.RefundStatus;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;

/** Persistence port for {@code payment_refunds}. */
public interface PaymentRefundRepository {

    PaymentRefund save(PaymentRefund refund);

    Optional<PaymentRefund> findById(String id);

    Optional<PaymentRefund> findByRefundReference(String refundReference);

    Optional<PaymentRefund> findByIdempotencyKey(String idempotencyKey);

    List<PaymentRefund> findByPaymentIdOrderByCreatedAtAsc(String paymentId);

    /** Sum of the payment's refunds that are in one of {@code statuses}; 0 when there are none. */
    BigDecimal sumAmount(String paymentId, Collection<RefundStatus> statuses);
}
