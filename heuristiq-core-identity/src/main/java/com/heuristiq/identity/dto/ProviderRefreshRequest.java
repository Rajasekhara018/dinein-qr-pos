package com.heuristiq.identity.dto;

import jakarta.validation.constraints.NotBlank;

public record ProviderRefreshRequest(
        String providerType,
        @NotBlank String refreshToken,
        String totp
) {
    public ProviderRefreshRequest(String providerType, String refreshToken) { this(providerType, refreshToken, null); }
    public ProviderRefreshRequest(@NotBlank String refreshToken) {
        this(null, refreshToken);
    }
}
