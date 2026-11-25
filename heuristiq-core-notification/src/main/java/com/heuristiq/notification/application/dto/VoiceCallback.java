package com.heuristiq.notification.application.dto;

/**
 * A voice provider's report on a call, already read from the provider's wire format by the inbound adapter.
 *
 * @param provider   concrete provider (exotel)
 * @param callSid    the provider's call id (the attempt's providerMessageId)
 * @param rawStatus  provider status text (completed, no-answer, busy, failed ...); may be null for a digit-only report
 * @param rawDigits  digits the caller pressed, as the provider sent them; may be null
 * @param entityType origin entity type carried by the call (CustomField); may be null, then taken from the attempt
 * @param entityId   origin entity id carried by the call; may be null
 * @param eventCode  origin event code carried by the call; may be null
 */
public record VoiceCallback(String provider, String callSid, String rawStatus, String rawDigits,
        String entityType, String entityId, String eventCode) {
}
