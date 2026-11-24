package com.railcab.dms.centralaudit;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * One DOMAIN event as sent to the audit service (a subset of its ingest contract). Holds no file contents, secrets or
 * personal data. {@code subjectType}, {@code subjectId} and {@code reasonCode} are the audit service's first-class
 * fields; this local type keeps DMS independent of the audit service's classes.
 *
 * @param occurredAt ISO-8601 instant text
 * @param outcome    SUCCESS, FAILURE or DENIED
 * @param subjectId  the document id when one exists, else null
 * @param details    short non-secret attributes (action, documentType, category, reasonCode, storageProvider,
 *                   scanVerdict, sizeBytes, message)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DmsAuditEvent(
        String eventId,
        String eventType,
        String occurredAt,
        String sourceService,
        String userId,
        String role,
        String feature,
        String module,
        String outcome,
        Long durationMs,
        String correlationId,
        String appChannel,
        String tenantId,
        String subjectType,
        String subjectId,
        String reasonCode,
        Map<String, Object> details) {
}
