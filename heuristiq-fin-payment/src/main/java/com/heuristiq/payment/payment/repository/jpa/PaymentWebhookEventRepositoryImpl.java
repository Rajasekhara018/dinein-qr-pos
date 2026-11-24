package com.heuristiq.paymentservice.payment.repository.jpa;

import com.heuristiq.paymentservice.payment.model.PaymentWebhookEvent;
import com.heuristiq.paymentservice.payment.repository.PaymentWebhookEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** JPA/Postgres adapter for {@link PaymentWebhookEventRepository}. */
@Repository
@RequiredArgsConstructor
class PaymentWebhookEventRepositoryImpl implements PaymentWebhookEventRepository {

    private final JpaPaymentWebhookEventRepository jpaRepository;

    @Override
    public PaymentWebhookEvent save(PaymentWebhookEvent event) {
        return jpaRepository.save(event);
    }

    @Override
    public Optional<PaymentWebhookEvent> findByProviderEventId(String providerEventId) {
        return jpaRepository.findByProviderEventId(providerEventId);
    }
}
