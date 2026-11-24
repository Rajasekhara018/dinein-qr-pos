package com.heuristiq.search.security;

/**
 * Fixed wire values of the service's request security: the trusted headers the API gateway sets, the identity used for
 * a gateway-authenticated call that names no user, the public path prefixes and the fixed error bodies.
 */
public final class SearchSecurityConstants {

    /** Shared secret the gateway injects on every forwarded request. */
    public static final String GATEWAY_TOKEN_HEADER = "X-Gateway-Token";
    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String USER_ROLE_HEADER = "X-User-Role";
    public static final String TENANT_ID_HEADER = "X-Tenant-Id";
    /** "true" when the caller's token allows looking across organizations (set by the gateway only). */
    public static final String ORG_WIDE_HEADER = "X-Org-Wide";
    /** "true" when an organization-wide caller asks to see every organization. */
    public static final String VIEW_ALL_ORGANIZATIONS_HEADER = "X-View-All-Organizations";
    /** Header value meaning yes, compared ignoring case. */
    public static final String TRUE_VALUE = "true";

    /** Principal of a gateway-authenticated call with no user (service to service). */
    public static final String INTERNAL_SERVICE_USER = "gateway";
    public static final String INTERNAL_SERVICE_ROLE = "INTERNAL_SERVICE";

    /** Spring Security authority prefix put in front of every role name. */
    public static final String ROLE_PREFIX = "ROLE_";
    /** Regex matching {@link #ROLE_PREFIX} at the start of a role, to strip it. */
    public static final String ROLE_PREFIX_PATTERN = "^" + ROLE_PREFIX;
    /** Authority of a service-to-service caller. */
    public static final String INTERNAL_SERVICE_AUTHORITY = ROLE_PREFIX + INTERNAL_SERVICE_ROLE;

    /** Servlet-path fragments the gateway-token check skips (health/metrics, OpenAPI docs, Swagger UI, Javadoc). */
    public static final String ACTUATOR_PATH_FRAGMENT = "/actuator/";
    public static final String API_DOCS_PATH_FRAGMENT = "/v3/api-docs";
    public static final String SWAGGER_UI_PATH_FRAGMENT = "/swagger-ui";
    public static final String JAVADOC_PATH_PREFIX = "/javadoc";

    public static final String UNAUTHORIZED_BODY =
            "{\"success\":false,\"message\":\"Unauthorized search request\",\"data\":null}";
    public static final String RATE_LIMIT_EXCEEDED_BODY =
            "{\"success\":false,\"message\":\"Rate limit exceeded. Please try again later.\",\"data\":null}";

    /** Redis key prefix of the per-caller rate-limit counter; the caller identifier is appended. */
    public static final String RATE_LIMIT_KEY_PREFIX = "search:ratelimit:api:";
    public static final String RATE_LIMIT_USER_PREFIX = "user:";
    public static final String RATE_LIMIT_IP_PREFIX = "ip:";
    /**
     * Counts one request and starts the window in a single atomic step: INCR, then EXPIRE when the counter is new or
     * has no expiry (a key left without one by the old two-step INCR + EXPIRE would otherwise block its owner forever).
     * KEYS[1] = counter key, ARGV[1] = window in seconds; returns the new count.
     */
    public static final String RATE_LIMIT_SCRIPT = "local count = redis.call('INCR', KEYS[1]) "
            + "if count == 1 or redis.call('TTL', KEYS[1]) == -1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end "
            + "return count";

    private SearchSecurityConstants() {
    }
}
