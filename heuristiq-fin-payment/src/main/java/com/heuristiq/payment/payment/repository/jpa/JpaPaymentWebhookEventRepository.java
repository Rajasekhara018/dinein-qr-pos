package com.heuristiq.paymentservice.payment.repository.jpa;

import com.heuristiq.paymentservice.payment.model.PaymentWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Spring Data JPA binding for {@code payment_webhook_events}. Not injected directly — see {@link PaymentWebhookEventRepositoryImpl}. */
interface JpaPaymentWebhookEventRepository extends JpaRepository<PaymentWebhookEvent, String> {

    Optional<PaymentWebhookEvent> findByProviderEventId(String providerEventId);
}
