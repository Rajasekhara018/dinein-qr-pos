package com.heuristiq.identity.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Optional body for {@code POST /identity/auth/logout}. The local session is always revoked from
 * the caller's own JWT (no fields needed for that). If {@code providerType} + {@code refreshToken}
 * (the external IdP's own refresh token, not UAM's) are also supplied, the external provider's
 * session is revoked too in the same call -- see
 * {@link com.heuristiq.identity.identity.IdentityProviderAuthRouter#logout}.
 */
@Schema(description = "Optional logout body. The caller's local session is always revoked from their own JWT with no "
        + "fields needed; supplying providerType + refreshToken also revokes the external IdP session in the same call.")
public record UnifiedLogoutRequest(
        @Schema(description = "External IdP identifier. If supplied together with refreshToken, that provider's session is also revoked.") String providerType,
        @Schema(description = "The external IdP's own refresh token (not UAM's), required only when providerType is supplied.") String refreshToken
) {
}
