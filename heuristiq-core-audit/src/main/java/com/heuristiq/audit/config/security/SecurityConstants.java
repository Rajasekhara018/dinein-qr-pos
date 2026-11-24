package com.railcab.audit.config.security;

/** Header names, role names and paths of the security configuration. */
public final class SecurityConstants {

    private SecurityConstants() {
    }

    /** Shared service-to-service credential header. */
    public static final String SERVICE_TOKEN_HEADER = "X-Rider-Service-Token";
    public static final String AUTHORIZATION_HEADER = "Authorization";
    public static final String BEARER_PREFIX = "Bearer ";
    public static final String ROLE_PREFIX = "ROLE_";
    /** Role carried by the tokens services mint for each other. */
    public static final String SERVICE_ROLE = "SERVICE";
    /** Token claim holding the organization of the session. */
    public static final String TENANT_CLAIM = "tenantId";
    /** Set by the gateway from the verified token for an organization-wide caller (clients can not set it). */
    public static final String ORG_WIDE_HEADER = "X-Org-Wide";
    /** Sent by an organization-wide caller who wants to look across organizations; ignored without {@link #ORG_WIDE_HEADER}. */
    public static final String VIEW_ALL_ORGANIZATIONS_HEADER = "X-View-All-Organizations";
    public static final String INTERNAL_PRINCIPAL = "internal-service";
    public static final String ANONYMOUS_PRINCIPAL = "anonymousUser";
    public static final String INTERNAL_PATTERN = "/api/v1/audit/internal/**";
    public static final String ACTUATOR_PATTERN = "/api/v1/audit/actuator/**";
    public static final String ROOT_ACTUATOR_PATTERN = "/actuator/**";
    public static final String UNAUTHENTICATED_CODE = "UNAUTHENTICATED";
    public static final String UNAUTHENTICATED_MESSAGE = "Authentication required";
    public static final int MIN_SECRET_BYTES = 32;
    public static final String ROLES_CLAIM = "roles";
    public static final String ROLE_CLAIM = "role";
    public static final String JWT_FAILURE_METRIC = "audit_jwt_validation_failures_total";
    public static final String METRIC_REASON_TAG = "reason";
    public static final String SERVICE_TOKEN_ON_USER_API = "ServiceTokenOnUserApi";
    public static final String[] DOC_PATTERNS = {"/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html",
            "/swagger-ui/**", "/javadoc", "/javadoc/**"};
}
