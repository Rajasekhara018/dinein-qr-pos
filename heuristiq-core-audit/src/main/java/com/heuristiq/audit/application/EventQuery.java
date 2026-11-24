package com.railcab.audit.application;

/**
 * The raw (string) filter parameters of the query endpoints; {@link AuditQueryService} validates and converts them.
 *
 * @param userId        exact user id
 * @param role          role, matching the primary role or any role
 * @param feature       feature
 * @param screenKey     screen key
 * @param eventType     event type name
 * @param outcome       outcome name
 * @param channel       channel name
 * @param privilegeId   privilege id
 * @param method        HTTP method
 * @param path          substring of the path
 * @param correlationId correlation id
 * @param from          ISO-8601 instant, inclusive
 * @param to            ISO-8601 instant, exclusive
 * @param subjectType   exact subject type, e.g. DOCUMENT (case-insensitive input)
 * @param subjectId     exact subject id
 * @param reasonCode    exact reason code (case-insensitive input)
 */
public record EventQuery(String userId, String role, String feature, String screenKey, String eventType,
                         String outcome, String channel, Integer privilegeId, String method, String path,
                         String correlationId, String from, String to, String subjectType, String subjectId,
                         String reasonCode) {

    /** A query without the subject and reason filters. */
    public EventQuery(String userId, String role, String feature, String screenKey, String eventType,
                      String outcome, String channel, Integer privilegeId, String method, String path,
                      String correlationId, String from, String to) {
        this(userId, role, feature, screenKey, eventType, outcome, channel, privilegeId, method, path,
                correlationId, from, to, null, null, null);
    }
}
