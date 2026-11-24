package com.railcab.nms.application.dto;

import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The COMMUNICATION event as sent to the audit service (a subset of its ingest contract). Holds no
 * rendered body, OTP or unmasked recipient; {@code details} values are plain strings. {@code tenantId} is the organization
 * of the notification (null for one stored before organizations), so the audit screen can separate organizations.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CommunicationAuditEvent(
        String eventId,
        String eventType,
        Instant occurredAt,
        String sourceService,
        String userId,
        String tenantId,
        String feature,
        String module,
        String outcome,
        Long durationMs,
        String correlationId,
        String requestId,
        String appChannel,
        Map<String, String> details) {
}
