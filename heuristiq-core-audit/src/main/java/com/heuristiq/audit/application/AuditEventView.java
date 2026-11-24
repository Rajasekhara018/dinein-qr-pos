package com.railcab.audit.application;

import com.railcab.audit.domain.AppChannel;
import com.railcab.audit.domain.AuditEvent;
import com.railcab.audit.domain.AuditEventType;
import com.railcab.audit.domain.AuditOutcome;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Read model returned by the query endpoints (the stored event without its retention bookkeeping). */
@Schema(description = "One audit event.")
public record AuditEventView(Long id, String eventId, AuditEventType eventType, Instant occurredAt,
                             Instant receivedAt, String sourceService, String userId, String userType,
                             String role, List<String> roles, List<Integer> privilegeIds, String feature,
                             String module, String screenKey, String method, String path, Integer statusCode,
                             AuditOutcome outcome, Long durationMs, String correlationId, String requestId,
                             String ipAddress, String userAgent, AppChannel appChannel, String tenantId,
                             Map<String, String> details, String subjectType, String subjectId,
                             String reasonCode) {

    /** Maps a stored event to its read model. */
    public static AuditEventView from(AuditEvent e) {
        return new AuditEventView(e.getId(), e.getEventId(), e.getEventType(), e.getOccurredAt(), e.getReceivedAt(),
                e.getSourceService(), e.getUserId(), e.getUserType(), e.getRole(), e.getRoles(), e.getPrivilegeIds(),
                e.getFeature(), e.getModule(), e.getScreenKey(), e.getMethod(), e.getPath(), e.getStatusCode(),
                e.getOutcome(), e.getDurationMs(), e.getCorrelationId(), e.getRequestId(), e.getIpAddress(),
                e.getUserAgent(), e.getAppChannel(), e.getTenantId(), e.getDetails(),
                e.getSubjectType(), e.getSubjectId(), e.getReasonCode());
    }
}
