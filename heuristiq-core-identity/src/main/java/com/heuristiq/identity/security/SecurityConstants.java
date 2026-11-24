package com.heuristiq.uam.security;

import java.util.List;

/**
 * Fixed values of the UAM security layer: header names, path patterns, authorities and password-hashing
 * defaults used by the filters and security configuration in this package.
 */
public final class SecurityConstants {

    /** Standard bearer-token header read by the JWT filter. */
    public static final String AUTHORIZATION_HEADER = "Authorization";

    /** Prefix of a bearer token in the {@link #AUTHORIZATION_HEADER} header. */
    public static final String BEARER_PREFIX = "Bearer ";

    /** Header carrying the shared service token on internal service-to-service calls. */
    public static final String INTERNAL_SERVICE_TOKEN_HEADER = "X-Rider-Service-Token";

    /** Authority granted to a caller that presented a valid internal service token. */
    public static final String SERVICE_AUTHORITY = "ROLE_SERVICE";

    /** Principal name given to a caller that presented a valid internal service token. */
    public static final String SERVICE_PRINCIPAL = "internal-service";

    /** Path pattern protected by the internal service filter chain. */
    public static final String INTERNAL_PATH = "/api/v1/uam/internal/**";

    /** Sign-in and token endpoints, open without a session. */
    public static final String PUBLIC_AUTH_PATH = "/api/v1/uam/identity/auth/**";

    /** Actuator endpoints, open without a session. */
    public static final String ACTUATOR_PATH = "/api/v1/uam/actuator/**";

    /** API documentation endpoints, open without a session. */
    public static final List<String> API_DOC_PATHS = List.of(
            "/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**", "/javadoc", "/javadoc/**");

    /** Prefix Spring Security expects on role authorities. */
    public static final String ROLE_AUTHORITY_PREFIX = "ROLE_";

    /** JWT claim carrying a single role. */
    public static final String ROLE_CLAIM = "role";

    /** JWT claim (and nested Keycloak field) carrying a list of roles. */
    public static final String ROLES_CLAIM = "roles";

    /** JWT claim: the organization the session works in. */
    public static final String TENANT_ID_CLAIM = "tenantId";

    /** Authentication methods of the session (RFC 8176 "amr"); written only for a biometric sign-in, as ["biometric"], so other tokens are unchanged. */
    public static final String AMR_CLAIM = "amr";

    /** JWT claim: the account's user name. */
    public static final String USERNAME_CLAIM = "username";

    /** JWT claim: the account's e-mail address. */
    public static final String EMAIL_CLAIM = "email";

    /** JWT claim: the account holder's full name. */
    public static final String FULL_NAME_CLAIM = "fullName";

    /** JWT claim: the account's user type. */
    public static final String USER_TYPE_CLAIM = "userType";

    /**
     * JWT claim: the ids of the franchises the account owns or works for, in the session's organization. Left out for an
     * account that is in no franchise. Services restrict what a franchise person sees to these (see the gateway's X-Franchise-Ids).
     */
    public static final String FRANCHISE_IDS_CLAIM = "franchiseIds";

    /** JWT claim: the ids of the franchises the account owns (a subset of {@link #FRANCHISE_IDS_CLAIM}); left out when none. */
    public static final String OWNED_FRANCHISE_IDS_CLAIM = "ownedFranchiseIds";

    /** JWT claim: the ids of the franchises where the account is an active agent (a subset of {@link #FRANCHISE_IDS_CLAIM}); left out when none. */
    public static final String AGENT_FRANCHISE_IDS_CLAIM = "agentFranchiseIds";

    /** Franchise service lookup of the franchises a person owns or works for (the {@link #FRANCHISE_IDS_CLAIM} source). */
    public static final String FRANCHISE_CLAIMS_PATH = "/api/v1/franchise/internal/claims";
    /** Franchise service lookup of the same franchises with owner and agent apart (the owned and agent claim sources). */
    public static final String FRANCHISE_ROLE_CLAIMS_PATH = FRANCHISE_CLAIMS_PATH + "/roles";
    public static final String FRANCHISE_TENANT_PARAM = "tenantId";
    public static final String FRANCHISE_USER_PARAM = "userId";
    /** Name of the franchise service in the resilience metrics and errors. */
    public static final String FRANCHISE_SERVICE_NAME = "franchise-service";

    /** JWT claim: codes of the applications the account may sign in to. */
    public static final String APPS_CLAIM = "apps";

    /** Value of {@link #ROLE_CLAIM} when the account has no role. */
    public static final String DEFAULT_ROLE = "USER";

    /** Keycloak JWT claim holding realm-level roles. */
    public static final String REALM_ACCESS_CLAIM = "realm_access";

    /** Keycloak JWT claim holding client-level roles. */
    public static final String RESOURCE_ACCESS_CLAIM = "resource_access";

    /** {@code identity-provider.token-validation} value meaning tokens are validated as external JWTs. */
    public static final String JWT_TOKEN_VALIDATION = "jwt";

    /** JSON field holding the message in the internal chain's 401/403 bodies. */
    public static final String ERROR_FIELD = "error";

    /** 401 message of the internal chain when no valid service token was presented. */
    public static final String SERVICE_AUTHENTICATION_REQUIRED_MESSAGE = "Service authentication required.";

    /** 403 message of the internal chain when the caller is not an internal service. */
    public static final String INTERNAL_CREDENTIAL_REQUIRED_MESSAGE = "Internal service credential required.";

    /** Default BCrypt cost factor when none is configured. */
    public static final int DEFAULT_BCRYPT_STRENGTH = 11;

    /** Default number of password-hash jobs that may wait for a worker. */
    public static final int DEFAULT_QUEUE_CAPACITY = 64;

    /** Default time in milliseconds a caller waits for a password-hash job. */
    public static final long DEFAULT_TIMEOUT_MS = 2000;

    /** Password-hash pool size meaning "automatic": every available core but {@link #HASH_POOL_RESERVED_CORES}. */
    public static final int AUTO_HASH_POOL_THREADS = 0;

    /**
     * Cores the automatic password-hash pool leaves free for the rest of the service. None: production UAM is sized as
     * one CPU per hash thread (2 CPUs = 2 threads, see the Production UAM Jenkinsfile), so reserving a core would halve
     * login capacity there. Set {@code railcab.uam.auth.password.hash-pool-threads} to leave cores free on bigger hosts.
     */
    public static final int HASH_POOL_RESERVED_CORES = 0;

    /** The password-hash pool never has fewer threads than this. */
    public static final int MIN_HASH_POOL_THREADS = 1;

    private SecurityConstants() {
    }
}
