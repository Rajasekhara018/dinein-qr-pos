package com.heuristiq.audit.adapter.out.persistence.jpa;

import com.heuristiq.audit.domain.AppChannel;
import com.heuristiq.audit.domain.AuditEvent;
import com.heuristiq.audit.domain.AuditEventType;
import com.heuristiq.audit.domain.AuditOutcome;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PostgreSQL row of an audit event (table {@code audit_events}, created by Flyway V1). The role and privilege
 * lists are stored as delimited text with a leading and trailing comma (for example {@code ,ADMIN,SUPPORT,}) so
 * "contains this role" is a plain {@code LIKE '%,ADMIN,%'} on any database.
 */
@Getter
@Setter
@Entity
@Table(name = "audit_events")
public class AuditEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "event_id", nullable = false, length = 64, updatable = false)
    private String eventId;
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 32)
    private AuditEventType eventType;
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;
    @Column(name = "expire_at", nullable = false)
    private Instant expireAt;
    @Column(name = "source_service", length = 64)
    private String sourceService;
    @Column(name = "user_id", length = 64)
    private String userId;
    @Column(name = "user_type", length = 32)
    private String userType;
    @Column(name = "role", length = 64)
    private String role;
    @Column(name = "roles", length = 512)
    private String roles;
    @Column(name = "privilege_ids", length = 256)
    private String privilegeIds;
    @Column(name = "feature", length = 100)
    private String feature;
    @Column(name = "module", length = 64)
    private String module;
    @Column(name = "screen_key", length = 100)
    private String screenKey;
    @Column(name = "method", length = 10)
    private String method;
    @Column(name = "path", length = 500)
    private String path;
    @Column(name = "status_code")
    private Integer statusCode;
    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", length = 16)
    private AuditOutcome outcome;
    @Column(name = "duration_ms")
    private Long durationMs;
    @Column(name = "correlation_id", length = 100)
    private String correlationId;
    @Column(name = "request_id", length = 100)
    private String requestId;
    @Column(name = "ip_address", length = 64)
    private String ipAddress;
    @Column(name = "user_agent", length = 300)
    private String userAgent;
    @Enumerated(EnumType.STRING)
    @Column(name = "app_channel", length = 32)
    private AppChannel appChannel;
    @Column(name = "tenant_id", length = 64)
    private String tenantId;
    @Column(name = "subject_type", length = 40)
    private String subjectType;
    @Column(name = "subject_id", length = 128)
    private String subjectId;
    @Column(name = "reason_code", length = 60)
    private String reasonCode;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details", columnDefinition = "jsonb")
    private Map<String, String> details;

    /** @return the entity for a domain event (the id is left to the database) */
    public static AuditEventEntity from(AuditEvent e) {
        AuditEventEntity row = new AuditEventEntity();
        row.eventId = e.getEventId();
        row.eventType = e.getEventType();
        row.occurredAt = e.getOccurredAt();
        row.receivedAt = e.getReceivedAt();
        row.expireAt = e.getExpireAt();
        row.sourceService = e.getSourceService();
        row.userId = e.getUserId();
        row.userType = e.getUserType();
        row.role = e.getRole();
        row.roles = join(e.getRoles());
        row.privilegeIds = join(e.getPrivilegeIds());
        row.feature = e.getFeature();
        row.module = e.getModule();
        row.screenKey = e.getScreenKey();
        row.method = e.getMethod();
        row.path = e.getPath();
        row.statusCode = e.getStatusCode();
        row.outcome = e.getOutcome();
        row.durationMs = e.getDurationMs();
        row.correlationId = e.getCorrelationId();
        row.requestId = e.getRequestId();
        row.ipAddress = e.getIpAddress();
        row.userAgent = e.getUserAgent();
        row.appChannel = e.getAppChannel();
        row.tenantId = e.getTenantId();
        row.subjectType = e.getSubjectType();
        row.subjectId = e.getSubjectId();
        row.reasonCode = e.getReasonCode();
        row.details = e.getDetails() == null ? null : new LinkedHashMap<>(e.getDetails());
        return row;
    }

    /** @return the domain event */
    public AuditEvent toDomain() {
        return AuditEvent.builder().id(id).eventId(eventId).eventType(eventType).occurredAt(occurredAt)
                .receivedAt(receivedAt).expireAt(expireAt).sourceService(sourceService).userId(userId)
                .userType(userType).role(role).roles(split(roles)).privilegeIds(splitInts(privilegeIds))
                .feature(feature).module(module).screenKey(screenKey).method(method).path(path)
                .statusCode(statusCode).outcome(outcome).durationMs(durationMs).correlationId(correlationId)
                .requestId(requestId).ipAddress(ipAddress).userAgent(userAgent).appChannel(appChannel)
                .tenantId(tenantId).subjectType(subjectType).subjectId(subjectId).reasonCode(reasonCode).details(details == null ? null : new LinkedHashMap<>(details)).build();
    }

    /** @return {@code ,a,b,} or null for an empty list */
    public static String join(List<?> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder(",");
        for (Object v : values) {
            sb.append(v).append(',');
        }
        return sb.toString();
    }

    private static List<String> split(String text) {
        List<String> out = new ArrayList<>();
        if (text != null) {
            for (String part : text.split(",")) {
                if (!part.isEmpty()) {
                    out.add(part);
                }
            }
        }
        return out;
    }

    private static List<Integer> splitInts(String text) {
        return split(text).stream().map(Integer::valueOf).toList();
    }
}
