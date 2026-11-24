package com.heuristiq.paymentservice.gatewayaudit;

import com.heuristiq.paymentservice.gatewayaudit.model.WebhookOutcome;

/**
 * What the webhook handler decided about one delivery. The raw payload and the signature value are
 * never part of it.
 *
 * @param provider       gateway that sent the webhook (e.g. RAZORPAY)
 * @param eventType      provider event name, e.g. {@code payment.captured}; null when the body was not read
 * @param providerEventId the provider's event id (or a digest of the body when it sent none)
 * @param signatureValid whether the signature verified
 * @param paymentId      internal payment (or wallet top-up) id when one was matched
 * @param tenantId       the organization of that payment or top-up, or null when none was matched
 */
public record WebhookObservation(String provider, String eventType, String providerEventId, boolean signatureValid,
                                 WebhookOutcome outcome, String paymentId, String tenantId) {

    public WebhookObservation(String provider, String eventType, String providerEventId, boolean signatureValid,
                              WebhookOutcome outcome, String paymentId) {
        this(provider, eventType, providerEventId, signatureValid, outcome, paymentId, null);
    }
}
