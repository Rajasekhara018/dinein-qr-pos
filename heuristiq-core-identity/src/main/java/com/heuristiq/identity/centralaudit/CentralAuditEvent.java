package com.heuristiq.identity.centralaudit;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/**
 * One DOMAIN event in the shape Rider_AuditService's {@code AuditEventInput} accepts (only the fields UAM fills).
 * Wire shape only; no logic.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CentralAuditEvent(
        String eventId,
        String eventType,
        Instant occurredAt,
        String sourceService,
        String userId,
        String feature,
        String module,
        String outcome,
        String correlationId,
        String requestId,
        String ipAddress,
        String userAgent,
        String tenantId,
        String subjectType,
        String subjectId,
        String reasonCode,
        Map<String, Object> details) {
}
