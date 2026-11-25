package com.heuristiq.payment.payment.repository.jpa;

import com.heuristiq.payment.payment.model.PaymentRefund;
import com.heuristiq.payment.payment.model.RefundStatus;
import com.heuristiq.payment.payment.repository.PaymentRefundRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;

/** JPA/Postgres adapter for {@link PaymentRefundRepository}. */
@Repository
@RequiredArgsConstructor
class PaymentRefundRepositoryImpl implements PaymentRefundRepository {

    private final JpaPaymentRefundRepository jpaRepository;

    @Override
    public PaymentRefund save(PaymentRefund refund) {
        return jpaRepository.save(refund);
    }

    @Override
    public Optional<PaymentRefund> findById(String id) {
        return jpaRepository.findById(id);
    }

    @Override
    public Optional<PaymentRefund> findByRefundReference(String refundReference) {
        return jpaRepository.findByRefundReference(refundReference);
    }

    @Override
    public Optional<PaymentRefund> findByIdempotencyKey(String idempotencyKey) {
        return jpaRepository.findByIdempotencyKey(idempotencyKey);
    }

    @Override
    public List<PaymentRefund> findByPaymentIdOrderByCreatedAtAsc(String paymentId) {
        return jpaRepository.findByPaymentIdOrderByCreatedAtAsc(paymentId);
    }

    @Override
    public BigDecimal sumAmount(String paymentId, Collection<RefundStatus> statuses) {
        BigDecimal sum = jpaRepository.sumAmount(paymentId, statuses);
        return sum == null ? BigDecimal.ZERO : sum;
    }
}
