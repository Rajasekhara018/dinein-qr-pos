package com.railcab.audit.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * One audit event as the service stores it, independent of the database. Never carries request or
 * response bodies, tokens, OTPs or card data: {@code details} holds a bounded set of non-secret string
 * attributes only, and personal values are masked before an event gets here (see
 * {@code com.railcab.audit.application.EventSanitizer}).
 */
@Getter
@Setter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class AuditEvent {
    /** Database id (sequence on MongoDB, identity on PostgreSQL); null before insert. */
    private Long id;
    /** Producer-supplied unique id; inserting the same one twice stores it once. */
    private String eventId;
    private AuditEventType eventType;
    private Instant occurredAt;
    private Instant receivedAt;
    /** When the retention class lets the event go (TTL index on MongoDB, purge job on both). */
    private Instant expireAt;
    private String sourceService;
    private String userId;
    /** CUSTOMER, DRIVER, STAFF, SERVICE or ANONYMOUS. */
    private String userType;
    /** Primary role. */
    private String role;
    private List<String> roles;
    private List<Integer> privilegeIds;
    /** Catalog service slug or feature name, e.g. {@code rider-config}. */
    private String feature;
    /** Catalog module, e.g. {@code CONFIGURATION}. */
    private String module;
    private String screenKey;
    private String method;
    private String path;
    private Integer statusCode;
    private AuditOutcome outcome;
    private Long durationMs;
    private String correlationId;
    private String requestId;
    private String ipAddress;
    private String userAgent;
    private AppChannel appChannel;
    private String tenantId;
    /** What the event is about, UPPER_SNAKE (DOCUMENT, PAYMENT ...); null when not subject-specific. */
    private String subjectType;
    /** Id of the subject (identifier characters only). */
    private String subjectId;
    /** Stable machine-readable reason, UPPER_SNAKE (VIRUS_DETECTED ...). */
    private String reasonCode;
    private Map<String, String> details;
}
