package com.railcab.nms.application.dto;

/**
 * Record published on {@code nms-voice-responses} (ride flow contract, section 4). Consumers are idempotent on
 * {@code eventId}.
 *
 * @param eventId    provider call id plus the status (or pressed digit) it reports; the same report always gives the same id
 * @param entityType origin entity type (RIDE for rides)
 * @param entityId   origin entity id (the ride id)
 * @param eventCode  origin event code (EARLY_DROP_CONFIRM_CALL)
 * @param digit      digit the customer pressed, or null
 * @param outcome    ANSWERED, NO_ANSWER or FAILED
 * @param occurredAt ISO-8601 UTC instant NMS received the report
 * @param tenantId   organization of the original message when known (extra field, may be null)
 */
public record VoiceResponseEvent(String eventId, String entityType, String entityId, String eventCode, String digit,
        String outcome, String occurredAt, String tenantId) {
}
