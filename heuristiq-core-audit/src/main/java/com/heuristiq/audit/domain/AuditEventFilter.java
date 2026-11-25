package com.heuristiq.audit.domain;

import java.time.Instant;

/**
 * Search criteria; every field is optional and combined with AND.
 *
 * @param userId        exact user id
 * @param role          matches the primary role or any of the roles
 * @param feature       exact feature
 * @param screenKey     exact screen key
 * @param eventType     event type
 * @param outcome       outcome
 * @param channel       client channel
 * @param privilegeId   matches when the event carries this privilege id
 * @param method        HTTP method (case-insensitive)
 * @param pathContains  case-insensitive substring of the path
 * @param correlationId exact correlation id
 * @param from          inclusive lower bound of occurredAt
 * @param to            exclusive upper bound of occurredAt
 * @param subjectType   exact subject type (null for any)
 * @param subjectId     exact subject id
 * @param reasonCode    exact reason code
 * @param tenantScope   organizations the reader may reach; null applies no organization filter (the query service
 *                      always sets it)
 */
public record AuditEventFilter(String userId, String role, String feature, String screenKey,
                               AuditEventType eventType, AuditOutcome outcome, AppChannel channel,
                               Integer privilegeId, String method, String pathContains,
                               String correlationId, Instant from, Instant to, String subjectType,
                               String subjectId, String reasonCode, TenantScope tenantScope) {

    /** A filter without the subject and reason criteria (older callers). */
    public AuditEventFilter(String userId, String role, String feature, String screenKey, AuditEventType eventType,
                            AuditOutcome outcome, AppChannel channel, Integer privilegeId, String method,
                            String pathContains, String correlationId, Instant from, Instant to,
                            TenantScope tenantScope) {
        this(userId, role, feature, screenKey, eventType, outcome, channel, privilegeId, method, pathContains,
                correlationId, from, to, null, null, null, tenantScope);
    }

    /** A filter with no organization limit (ports used by trusted in-process code and tests). */
    public AuditEventFilter(String userId, String role, String feature, String screenKey, AuditEventType eventType,
                            AuditOutcome outcome, AppChannel channel, Integer privilegeId, String method,
                            String pathContains, String correlationId, Instant from, Instant to) {
        this(userId, role, feature, screenKey, eventType, outcome, channel, privilegeId, method, pathContains,
                correlationId, from, to, null);
    }
}
