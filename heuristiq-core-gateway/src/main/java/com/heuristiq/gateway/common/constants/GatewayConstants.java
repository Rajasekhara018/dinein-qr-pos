package com.heuristiq.gateway.common.constants;

import java.util.List;

public final class GatewayConstants {

    private GatewayConstants() {}

    public static final String BEARER_PREFIX         = "Bearer ";
    public static final String INTERNAL_HEADER_NAME  = "X-Internal-Secret-Key";

    // Headers forwarded downstream so services know who made the request
    public static final String X_USER_ID             = "X-User-Id";
    /** First/primary role only — kept for backward compatibility with downstream services'
     * single-role GatewayAuthFilter (AdminAPI, BookingsAPI, DMS). Do not use this for privilege
     * resolution; see {@link #X_USER_ROLES}. */
    public static final String X_USER_ROLE           = "X-User-Role";
    /** Every role the caller holds, comma-joined (see {@link #ROLE_LIST_DELIMITER}). The gateway's
     * own {@code PrivilegeBasedAccessFilter} reads this so a user with more than one role doesn't
     * silently lose the privileges of every role but the first. */
    public static final String X_USER_ROLES          = "X-User-Roles";
    /** The organization the caller's session is in (the token's {@code tenantId} claim). Set by the gateway only, from
     * the verified token, and always stripped from incoming requests; services trust it together with
     * {@link #X_GATEWAY_TOKEN} to return only that organization's data. Absent when the token carries no organization. */
    public static final String X_TENANT_ID           = "X-Tenant-Id";
    /** "true" when the token's {@code orgWide} claim is set: the caller may look across organizations. Set by the gateway
     * only, from the verified token, and always stripped from incoming requests. */
    public static final String X_ORG_WIDE            = "X-Org-Wide";
    public static final String ROLE_LIST_DELIMITER   = ",";
    /** Injected by the gateway on every forwarded request. Downstream services verify this
     * header to confirm the request came through the gateway, not a direct caller. */
    public static final String X_GATEWAY_TOKEN       = "X-Gateway-Token";
    /** Service-to-service credential of the platform; the gateway never adds it, so any incoming copy is a forgery attempt. */
    public static final String X_RIDER_SERVICE_TOKEN = "X-Rider-Service-Token";
    /** Set to {@code true} by the gateway when one of the caller's roles holds the
     * {@link #ELEVATED_ACCESS_PRIVILEGE_ID} privilege. Services trust it only together with a valid
     * {@link #X_GATEWAY_TOKEN}; it replaces their checks of role names (ADMIN, SUPER_ADMIN). */
    public static final String X_USER_ELEVATED       = "X-User-Elevated";
    /** privilege.json entry PLATFORM_ELEVATED_ACCESS: lets a role bypass per-owner checks in services. */
    public static final int ELEVATED_ACCESS_PRIVILEGE_ID = 444;
    public static final String PERMISSION_CATALOG_VERSION_ATTRIBUTE = "privilegeCatalogVersion";
    public static final int PERMISSION_CATALOG_VERSION = 2;

    // JSON error response field names
    public static final String JSON_ERROR_KEY        = "error";
    public static final String JSON_MESSAGE_KEY      = "message";

    // Spring Security role constants
    public static final String ROLE_INTERNAL_SERVICE  = "ROLE_INTERNAL_SERVICE";
    public static final String INTERNAL_SERVICE       = "internal_service";
    // Role value forwarded in X-User-Role for internal service tokens (no ROLE_ prefix)
    public static final String INTERNAL_SERVICE_ROLE  = "INTERNAL_SERVICE";

    // Customer-facing role values (as carried in JWT role claim, no ROLE_ prefix)
    public static final String CUSTOMER_ROLE          = "CUSTOMER";
    public static final String DRIVER_ROLE            = "DRIVER";

    /** Spring Security authority prefix put in front of every role name. */
    public static final String ROLE_PREFIX            = "ROLE_";
    /** Regex matching {@link #ROLE_PREFIX} at the start of an authority, to strip it. */
    public static final String ROLE_PREFIX_PATTERN    = "^" + ROLE_PREFIX;

    /**
     * Swagger/OpenAPI and Javadoc paths: served without a JWT, framed by the docs UI and never privilege-checked.
     * Shared by {@code GatewaySecurityConfig} (docs security chain) and {@code PrivilegeBasedAccessFilter} (bypass).
     */
    public static final List<String> DOCS_PATH_PATTERNS = List.of(
            "/v3/api-docs", "/v3/api-docs/**",
            "/swagger-ui.html", "/swagger-ui/**",
            "/api/*/v3/api-docs", "/api/*/v3/api-docs/**",
            "/api/*/swagger-ui.html", "/api/*/swagger-ui/**",
            // Rider microservices sit one segment deeper (/api/rider/<svc>/...).
            "/api/rider/*/v3/api-docs", "/api/rider/*/v3/api-docs/**",
            "/api/rider/*/swagger-ui.html", "/api/rider/*/swagger-ui/**",
            // Javadoc pages -- same no-JWT treatment as the Swagger docs.
            "/javadoc", "/javadoc/**",
            "/api/*/javadoc", "/api/*/javadoc/**",
            "/api/rider/*/javadoc", "/api/rider/*/javadoc/**"
    );
}
