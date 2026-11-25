package com.heuristiq.identity.dto;

import com.heuristiq.identity.enums.UamEnums;

import jakarta.validation.constraints.NotBlank;

public record ProviderLoginRequest(
        String providerType,
        String grantType,
        String username,
        String password,
        String totp,
        String authorizationCode,
        String redirectUri,
        // Only used to decide the role assigned if this is the FIRST login for this identity
        // (a brand-new local UamUser gets created) -- defaults to ADMIN since Keycloak/OIDC SSO
        // in this system is an enterprise/staff login path, not customer/driver self-service
        // (those already have OTP/Google). Ignored once the user already exists locally.
        UamEnums.UserType userType
) {
    public ProviderLoginRequest(@NotBlank String username, @NotBlank String password, String totp) {
        this(null, "password", username, password, totp, null, null, null);
    }
}
