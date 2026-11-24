package com.heuristiq.payment.gatewayaudit;

import java.time.Instant;
import java.util.Map;

/**
 * A PAYMENT_GATEWAY audit event before serialisation. {@code eventId} is deterministic (the call id
 * or webhook row id) so redelivery is idempotent at the audit service.
 *
 * @param outcome    SUCCESS or FAILURE as the audit service understands it
 * @param statusCode HTTP status of the gateway reply when known, else null
 * @param details    non-secret attributes (the audit service keeps at most 20)
 * @param tenantId   the organization of the payment or caller the event is about, or null when there is none
 */
public record GatewayAuditEvent(String eventId, Instant occurredAt, String outcome, Integer statusCode,
                                Long durationMs, String correlationId, Map<String, Object> details,
                                String tenantId) {

    /** An event with no organization (stored before organizations, or no caller to take one from). */
    public GatewayAuditEvent(String eventId, Instant occurredAt, String outcome, Integer statusCode,
                             Long durationMs, String correlationId, Map<String, Object> details) {
        this(eventId, occurredAt, outcome, statusCode, durationMs, correlationId, details, null);
    }
}
