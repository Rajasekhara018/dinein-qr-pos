package com.heuristiq.payment.payment.repository.jpa;

import com.heuristiq.payment.payment.model.PaymentRefund;
import com.heuristiq.payment.payment.model.RefundStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;

/** Spring Data JPA binding for {@code payment_refunds}. Not injected directly. */
interface JpaPaymentRefundRepository extends JpaRepository<PaymentRefund, String> {

    Optional<PaymentRefund> findByRefundReference(String refundReference);

    Optional<PaymentRefund> findByIdempotencyKey(String idempotencyKey);

    List<PaymentRefund> findByPaymentIdOrderByCreatedAtAsc(String paymentId);

    @Query("SELECT COALESCE(SUM(r.amount), 0) FROM PaymentRefund r "
            + "WHERE r.paymentId = :paymentId AND r.status IN :statuses")
    BigDecimal sumAmount(@Param("paymentId") String paymentId, @Param("statuses") Collection<RefundStatus> statuses);
}
