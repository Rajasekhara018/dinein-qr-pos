package com.railcab.audit.adapter.out.persistence.mongo;

import static com.railcab.audit.adapter.out.persistence.PersistenceConstants.*;
import com.railcab.audit.domain.AppChannel;
import com.railcab.audit.domain.AuditEvent;
import com.railcab.audit.domain.AuditEventType;
import com.railcab.audit.domain.AuditOutcome;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MongoDB representation of an {@link AuditEvent} (collection {@code audit_events}). {@code eventId} is unique
 * (idempotent ingest) and {@code expireAt} carries a TTL index with zero delay, so MongoDB removes each event
 * when its own retention class says so. The compound indexes serve the audit screen's filters.
 */
@Document(collection = AUDIT_EVENTS_COLLECTION)
@CompoundIndex(name = "idx_audit_user_time", def = "{'userId': 1, 'occurredAt': -1}")
@CompoundIndex(name = "idx_audit_type_time", def = "{'eventType': 1, 'occurredAt': -1}")
@CompoundIndex(name = "idx_audit_feature_time", def = "{'feature': 1, 'occurredAt': -1}")
@CompoundIndex(name = "idx_audit_screen_time", def = "{'screenKey': 1, 'occurredAt': -1}")
@CompoundIndex(name = "idx_audit_subject_time", def = "{'subjectType': 1, 'subjectId': 1, 'occurredAt': -1}")
@CompoundIndex(name = "idx_audit_reason_time", def = "{'reasonCode': 1, 'occurredAt': -1}")
@CompoundIndex(name = "idx_audit_tenant_time", def = "{'tenantId': 1, 'occurredAt': -1}")
@Getter
@Setter
public class AuditEventDocument {

    /** Surrogate key from the {@code audit_events} sequence. */
    @Id
    private Long id;
    @Indexed(name = "uk_audit_event_id", unique = true)
    private String eventId;
    private AuditEventType eventType;
    @Indexed(name = "idx_audit_occurred_at", direction = org.springframework.data.mongodb.core.index.IndexDirection.DESCENDING)
    private Instant occurredAt;
    private Instant receivedAt;
    /** TTL index: the document is removed once this instant has passed. */
    @Indexed(name = "ttl_audit_expire_at", expireAfter = "0s")
    private Instant expireAt;
    private String sourceService;
    private String userId;
    private String userType;
    @Indexed(name = "idx_audit_role")
    private String role;
    @Indexed(name = "idx_audit_roles")
    private List<String> roles;
    @Indexed(name = "idx_audit_privilege_ids")
    private List<Integer> privilegeIds;
    private String feature;
    private String module;
    private String screenKey;
    private String method;
    private String path;
    private Integer statusCode;
    @Indexed(name = "idx_audit_outcome")
    private AuditOutcome outcome;
    private Long durationMs;
    @Indexed(name = "idx_audit_correlation")
    private String correlationId;
    private String requestId;
    private String ipAddress;
    private String userAgent;
    private AppChannel appChannel;
    private String tenantId;
    private String subjectType;
    private String subjectId;
    private String reasonCode;
    private Map<String, String> details;

    /** @return the document for a domain event */
    public static AuditEventDocument from(AuditEvent e) {
        AuditEventDocument d = new AuditEventDocument();
        d.id = e.getId();
        d.eventId = e.getEventId();
        d.eventType = e.getEventType();
        d.occurredAt = e.getOccurredAt();
        d.receivedAt = e.getReceivedAt();
        d.expireAt = e.getExpireAt();
        d.sourceService = e.getSourceService();
        d.userId = e.getUserId();
        d.userType = e.getUserType();
        d.role = e.getRole();
        d.roles = e.getRoles();
        d.privilegeIds = e.getPrivilegeIds();
        d.feature = e.getFeature();
        d.module = e.getModule();
        d.screenKey = e.getScreenKey();
        d.method = e.getMethod();
        d.path = e.getPath();
        d.statusCode = e.getStatusCode();
        d.outcome = e.getOutcome();
        d.durationMs = e.getDurationMs();
        d.correlationId = e.getCorrelationId();
        d.requestId = e.getRequestId();
        d.ipAddress = e.getIpAddress();
        d.userAgent = e.getUserAgent();
        d.appChannel = e.getAppChannel();
        d.tenantId = e.getTenantId();
        d.subjectType = e.getSubjectType();
        d.subjectId = e.getSubjectId();
        d.reasonCode = e.getReasonCode();
        d.details = e.getDetails() == null ? null : new LinkedHashMap<>(e.getDetails());
        return d;
    }

    /** @return the domain event */
    public AuditEvent toDomain() {
        return AuditEvent.builder().id(id).eventId(eventId).eventType(eventType).occurredAt(occurredAt)
                .receivedAt(receivedAt).expireAt(expireAt).sourceService(sourceService).userId(userId)
                .userType(userType).role(role).roles(roles == null ? List.of() : roles)
                .privilegeIds(privilegeIds == null ? List.of() : privilegeIds).feature(feature).module(module)
                .screenKey(screenKey).method(method).path(path).statusCode(statusCode).outcome(outcome)
                .durationMs(durationMs).correlationId(correlationId).requestId(requestId).ipAddress(ipAddress)
                .userAgent(userAgent).appChannel(appChannel).tenantId(tenantId)
                .subjectType(subjectType).subjectId(subjectId).reasonCode(reasonCode)
                .details(details == null ? null : new LinkedHashMap<>(details)).build();
    }
}
