package com.heuristiq.identity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * The single request shape for {@code POST /identity/auth/refresh}. If {@code providerType} is
 * present, {@code refreshToken} is redeemed against that external IdP (Keycloak/OIDC) and the
 * result re-bridged onto a local session, exactly like {@code /login} does -- see
 * {@link com.heuristiq.identity.identity.IdentityProviderAuthRouter#refresh}. Otherwise
 * {@code refreshToken} is UAM's own rotating local refresh token, handled by
 * {@code AuthService.refresh}. Either way the response is the same {@code LoginResponse} shape.
 */
@Schema(description = "Single request shape for token refresh, local or external-IdP.")
public record UnifiedRefreshRequest(
        @Schema(description = "The refresh token to redeem -- UAM's own local token, or (if providerType is set) the external IdP's token.")
        @NotBlank String refreshToken,
        @Schema(description = "External IdP identifier. If present, refreshToken is redeemed against that provider instead of locally.") String providerType
) {
}
