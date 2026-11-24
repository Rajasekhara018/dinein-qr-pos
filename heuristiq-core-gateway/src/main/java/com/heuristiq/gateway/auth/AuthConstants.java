package com.heuristiq.gateway.auth;

import java.time.Duration;
import java.util.Map;

/**
 * Fixed values of the gateway's token authentication (the auth providers and the JWT filter). Per-environment values
 * (IdP URLs, client ids, TTLs that are configurable) live in {@code GatewayProperties} / {@code @Value} settings.
 */
public final class AuthConstants {

    /** How long a blocking token-introspection call to an external IdP (Keycloak, OIDC) may take. */
    public static final Duration INTROSPECTION_TIMEOUT = Duration.ofSeconds(5);

    /** Upper bound of the per-provider introspection result cache. */
    public static final int INTROSPECTION_CACHE_MAX_SIZE = 2000;

    /** How long a Keycloak introspection result is cached. */
    public static final int KEYCLOAK_CACHE_TTL_SECONDS = 15;

    /** Normalised Keycloak role name to RailCab AdminRole name. */
    public static final Map<String, String> KEYCLOAK_ROLE_MAP = Map.of(
            "super_admin",          "SUPER_ADMIN",
            "railcab_super_admin",  "SUPER_ADMIN",
            "manager",              "MANAGER",
            "railcab_manager",      "MANAGER",
            "accountant",           "ACCOUNTANT",
            "railcab_accountant",   "ACCOUNTANT",
            "support",              "SUPPORT",
            "railcab_support",      "SUPPORT",
            "auditor",              "AUDITOR"
    );

    /** Issuer of tokens minted by UAM; such accounts' status is managed by UAM, not the legacy user collections. */
    public static final String UAM_TOKEN_ISSUER = "railcab-uam";

    /** JWT claim names read by {@code HmacJwtAuthProvider}. */
    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_ROLES = "roles";
    public static final String CLAIM_TENANT_ID = "tenantId";
    public static final String CLAIM_ORG_WIDE = "orgWide";

    private AuthConstants() {
    }
}
