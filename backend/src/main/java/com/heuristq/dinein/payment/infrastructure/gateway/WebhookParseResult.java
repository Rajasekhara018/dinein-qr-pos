package com.heuristq.dinein.payment.infrastructure.gateway;

import java.util.List;

/**
 * @param signatureValid false means the request must be rejected (400) and nothing processed
 * @param eventId        provider's unique event id used for de-duplication (may be null)
 * @param payloadJson    body as JSON for the audit log (form-encoded providers are converted)
 */
public record WebhookParseResult(boolean signatureValid, String eventId, String eventType, String payloadJson,
                                 List<GatewayEvent> events) {

    public static WebhookParseResult invalid(String eventType, String payloadJson) {
        return new WebhookParseResult(false, null, eventType, payloadJson, List.of());
    }

    /** Normalised event; {@code payment} is set for payment events, {@code refundId}/{@code providerPaymentId} for refunds. */
    public record GatewayEvent(Type type, ProviderPayment payment, String providerPaymentId, String refundId) {

        public enum Type { PAYMENT_CAPTURED, PAYMENT_AUTHORIZED, PAYMENT_FAILED, REFUND_PROCESSED, REFUND_FAILED }
    }
}
