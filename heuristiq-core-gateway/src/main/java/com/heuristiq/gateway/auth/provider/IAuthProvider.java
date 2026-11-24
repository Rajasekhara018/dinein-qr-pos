package com.heuristiq.gateway.auth.provider;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.List;

/**
 * Abstraction for token validation and identity extraction at the gateway.
 *
 * <p>Three implementations are provided, all always registered as beans:
 * <ul>
 *   <li>{@link HmacJwtAuthProvider} — validates HMAC-SHA256 tokens issued by AdminAPI
 *       and BookingsAPI. {@link AuthProviderType#HMAC}.</li>
 *   <li>{@link KeycloakAuthProvider} — delegates validation to a Keycloak realm's token
 *       introspection endpoint. {@link AuthProviderType#KEYCLOAK}.</li>
 *   <li>{@link OidcAuthProvider} — delegates validation to any RFC 7662-compatible
 *       introspection endpoint (Auth0, Okta, Azure AD, or any other generic OIDC provider).
 *       {@link AuthProviderType#OIDC}. Mirrors UAM's generic {@code OidcIdentityProviderAuthService}
 *       — one config-driven implementation instead of a provider-specific class per vendor.</li>
 * </ul>
 *
 * <p>{@link AuthProviderRouter} resolves which one is active from
 * {@code railcab.gateway.identity-provider.type} — switching providers is a config change,
 * not a Spring profile change. Adding a new IdP means implementing this interface and
 * returning a new {@link AuthProviderType} constant from {@link #providerType()}; no other
 * code changes are required.
 */
public interface IAuthProvider {

    /**
     * Identifies which identity provider this implementation validates tokens against.
     * Used by {@link AuthProviderRouter} to select the active provider.
     */
    AuthProviderType providerType();

    /**
     * Returns true if the token signature is valid and it has not expired.
     */
    boolean isTokenValid(String token);

    /**
     * Returns true if the token's expiry claim is in the past.
     */
    boolean isTokenExpired(String token);

    /**
     * Extracts the user identifier (subject / userId) from the token.
     * Returns null if the token cannot be parsed.
     */
    String extractUserId(String token);

    /**
     * Extracts the caller's primary role from the token.
     * Returns null if absent.
     */
    String extractRole(String token);

    /**
     * Extracts every role the caller holds (a caller can have more than one, e.g. a UAM user
     * assigned multiple {@code UamRole}s). Default implementation falls back to
     * {@link #extractRole(String)} wrapped in a singleton list, for providers that only ever
     * carry one role. Override when the underlying token/introspection response exposes a full
     * role list (see {@link HmacJwtAuthProvider}, {@link KeycloakAuthProvider},
     * {@link OidcAuthProvider}) so {@link #toAuthentication(String)} can grant every role's
     * privileges, not just the first.
     */
    default List<String> extractRoles(String token) {
        String role = extractRole(token);
        return role == null ? List.of() : List.of(role);
    }

    /**
     * Builds a fully authenticated Spring Security principal from a validated token.
     * Called only after {@link #isTokenValid(String)} returns true.
     */
    UsernamePasswordAuthenticationToken toAuthentication(String token);

    /**
     * Returns true when account status is managed by the issuing identity system,
     * so the gateway must not check legacy AdminAPI/BookingsAPI user collections.
     */
    default boolean isExternallyManagedToken(String token) {
        return false;
    }

    /**
     * Checks a token once and returns everything the gateway needs from it for one request: whether it is accepted,
     * why not (expired or invalid), the caller's principal, and whether account status is managed externally. The
     * default combines the single-purpose methods above in the order the gateway has always called them; a provider
     * whose methods each re-parse the token (see {@link HmacJwtAuthProvider}) overrides it to parse only once.
     */
    default TokenVerification verify(String token) {
        if (!isTokenValid(token)) {
            return TokenVerification.rejected(isTokenExpired(token));
        }
        UsernamePasswordAuthenticationToken authentication = toAuthentication(token);
        return TokenVerification.accepted(authentication, isExternallyManagedToken(token));
    }
}
