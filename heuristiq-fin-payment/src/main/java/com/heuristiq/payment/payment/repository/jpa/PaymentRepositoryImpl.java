package com.heuristiq.payment.payment.repository.jpa;

import com.heuristiq.payment.payment.gateway.model.PaymentGatewayType;
import com.heuristiq.payment.payment.model.Payment;
import com.heuristiq.payment.payment.model.PaymentStatus;
import com.heuristiq.payment.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** JPA/Postgres adapter for {@link PaymentRepository}. */
@Repository
@RequiredArgsConstructor
class PaymentRepositoryImpl implements PaymentRepository {

    private final JpaPaymentRepository jpaRepository;

    @Override
    public Payment save(Payment payment) {
        return jpaRepository.save(payment);
    }

    @Override
    public Optional<Payment> findById(String paymentId) {
        return jpaRepository.findById(paymentId);
    }

    @Override
    public Optional<Payment> findByIdForUpdate(String paymentId) {
        return jpaRepository.findByIdForUpdate(paymentId);
    }

    @Override
    public Optional<Payment> findByIdempotencyKey(String idempotencyKey) {
        return jpaRepository.findByIdempotencyKey(idempotencyKey);
    }

    @Override
    public Optional<Payment> findByProviderOrderId(String providerOrderId) {
        return jpaRepository.findByProviderOrderId(providerOrderId);
    }

    @Override
    public Optional<Payment> findByProviderPaymentId(String providerPaymentId) {
        return jpaRepository.findByProviderPaymentId(providerPaymentId);
    }

    @Override
    public List<Payment> findByStatusAndUpdatedAtBefore(PaymentStatus status, LocalDateTime threshold) {
        return jpaRepository.findByStatusAndUpdatedAtBefore(status, threshold);
    }

    @Override
    public List<Payment> findPollCandidatesAfter(List<PaymentStatus> statuses, List<PaymentGatewayType> gateways,
                                                 String afterPaymentId, int limit) {
        return jpaRepository.findPollCandidatesAfter(statuses, gateways, afterPaymentId, PageRequest.of(0, limit));
    }
}
