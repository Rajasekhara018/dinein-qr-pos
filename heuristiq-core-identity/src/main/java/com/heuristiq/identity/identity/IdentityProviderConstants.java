package com.heuristiq.identity.identity;

import java.time.Duration;

/**
 * Fixed values shared by the external identity-provider adapters (Keycloak and generic OIDC): OAuth 2.0 grant
 * types and form parameter names, the blocking timeout and the resilience4j instance names.
 */
public final class IdentityProviderConstants {

    /** How long a blocking call to an identity provider may take. */
    public static final Duration CALL_TIMEOUT = Duration.ofSeconds(10);

    /** resilience4j instance name (bulkhead, circuit breaker, retry) for Keycloak calls. */
    public static final String KEYCLOAK_RESILIENCE = "keycloak";

    /** resilience4j instance name (bulkhead, circuit breaker, retry) for generic OIDC provider calls. */
    public static final String OIDC_RESILIENCE = "oidc-provider";

    /** OAuth 2.0 grant types. */
    public static final String PASSWORD_GRANT = "password";
    public static final String AUTHORIZATION_CODE_GRANT = "authorization_code";
    public static final String REFRESH_TOKEN_GRANT = "refresh_token";

    /** OAuth 2.0 / OIDC token-endpoint form parameter names. */
    public static final String PARAM_GRANT_TYPE = "grant_type";
    public static final String PARAM_USERNAME = "username";
    public static final String PARAM_PASSWORD = "password";
    public static final String PARAM_CODE = "code";
    public static final String PARAM_REDIRECT_URI = "redirect_uri";
    public static final String PARAM_TOTP = "totp";
    public static final String PARAM_REFRESH_TOKEN = "refresh_token";
    public static final String PARAM_TOKEN = "token";
    public static final String PARAM_CLIENT_ID = "client_id";
    public static final String PARAM_CLIENT_SECRET = "client_secret";
    public static final String PARAM_SCOPE = "scope";
    public static final String PARAM_AUDIENCE = "audience";

    /** Shown when the identity provider cannot be reached. */
    public static final String PROVIDER_UNAVAILABLE_MESSAGE = "Identity provider is currently unavailable.";

    private IdentityProviderConstants() {
    }
}
