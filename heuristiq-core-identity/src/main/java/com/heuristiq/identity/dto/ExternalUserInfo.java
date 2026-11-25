package com.heuristiq.identity.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Standard OIDC userinfo endpoint response (RFC-shaped: sub/email/name/preferred_username) —
 * fetched by {@link com.heuristiq.identity.identity.IdentityProviderAuthService#fetchUserInfo(String)}
 * right after a successful provider login/refresh, so UAM can resolve/create a matching local
 * UamUser and issue its own JWT instead of handing back the external provider's token as-is.
 * Ignores every provider-specific extra claim (realm_access, resource_access, etc.).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExternalUserInfo(
        String sub,
        String email,
        String name,
        @JsonProperty("preferred_username") String preferredUsername
) {
}
