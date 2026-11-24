package com.heuristiq.payment.payment.repository;

import com.heuristiq.payment.payment.model.PaymentWebhookEvent;

import java.util.Optional;

/**
 * Database-agnostic persistence port for {@code payment_webhook_events}. Implementations
 * (e.g. {@code jpa.PaymentWebhookEventRepositoryImpl}) own the storage-specific details.
 */
public interface PaymentWebhookEventRepository {

    PaymentWebhookEvent save(PaymentWebhookEvent event);

    Optional<PaymentWebhookEvent> findByProviderEventId(String providerEventId);
}
