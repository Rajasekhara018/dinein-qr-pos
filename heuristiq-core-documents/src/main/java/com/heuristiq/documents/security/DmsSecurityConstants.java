package com.railcab.dms.security;

import com.railcab.dms.common.DmsCommonConstants;

/** Fixed values of DMS request authentication: gateway headers, roles, authorities, open paths and JWT claim names. */
public final class DmsSecurityConstants {

    private DmsSecurityConstants() {
    }

    // ---- headers the gateway sets
    public static final String GATEWAY_TOKEN_HEADER = "X-Gateway-Token";
    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String USER_ROLE_HEADER = "X-User-Role";
    public static final String TENANT_ID_HEADER = "X-Tenant-Id";

    // ---- principals, roles and authorities
    /** User id recorded for a gateway call that carries no user. */
    public static final String INTERNAL_SERVICE_USER = "gateway";
    /** Role of a trusted service-to-service call (no user, no organization of its own). */
    public static final String INTERNAL_SERVICE_ROLE = "INTERNAL_SERVICE";
    /** Spring Security's role prefix. */
    public static final String ROLE_PREFIX = "ROLE_";
    /** Regex matching a leading {@link #ROLE_PREFIX}, stripped before a role is compared. */
    public static final String ROLE_PREFIX_REGEX = "^" + ROLE_PREFIX;
    /** Authority only {@link InternalServiceAuthFilter} grants; the internal endpoints demand it. */
    public static final String SERVICE_AUTHORITY = "SERVICE_CREDENTIAL";
    /** Role a service JWT must carry to call the internal endpoints. */
    public static final String SERVICE_ROLE = "SERVICE";
    /** User id recorded for an authenticated service-to-service call. */
    public static final String CALLER_NAME = "workflow-service";
    /** Shortest platform JWT secret (in bytes) accepted for verifying service tokens. */
    public static final int MIN_SECRET_BYTES = 32;

    // ---- JWT claims
    public static final String CLAIM_ROLES = "roles";
    public static final String CLAIM_ROLE = "role";

    // ---- paths
    /** Path prefix of the service-to-service endpoints. */
    public static final String INTERNAL_PATH_PREFIX = "/api/v1/dms/internal/";
    /** Ant pattern of the service-to-service endpoints. */
    public static final String INTERNAL_PATH_PATTERN = INTERNAL_PATH_PREFIX + "**";
    /** Ant pattern of the signed-token download endpoint, open without a session. */
    public static final String PUBLIC_DOWNLOAD_PATTERN = DmsCommonConstants.PUBLIC_DOWNLOAD_PATH + "**";
    /** Fragment of the public download path skipped by the gateway filter. */
    public static final String PUBLIC_DOWNLOAD_SEGMENT = "/documents/public/";
    public static final String ACTUATOR_SEGMENT = "/actuator/";
    public static final String ACTUATOR_PATTERN = ACTUATOR_SEGMENT + "**";
    public static final String API_DOCS_PATH = "/v3/api-docs";
    public static final String API_DOCS_PATTERN = API_DOCS_PATH + "/**";
    public static final String SWAGGER_UI_PATH = "/swagger-ui";
    public static final String SWAGGER_UI_HTML = SWAGGER_UI_PATH + ".html";
    public static final String SWAGGER_UI_PATTERN = SWAGGER_UI_PATH + "/**";
    public static final String JAVADOC_PATH = "/javadoc";
    public static final String JAVADOC_PATTERN = JAVADOC_PATH + "/**";

    // ---- responses
    /** Body of the 401 both authentication filters answer. */
    public static final String UNAUTHORIZED_BODY = "{\"success\":false,\"message\":\"Unauthorized DMS request\",\"data\":null}";
}
