package com.heuristiq.uam.dto;

import com.heuristiq.uam.enums.UamEnums;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The single request shape for {@code POST /identity/auth/login} -- every login mechanism this
 * service supports (local username/password, Google, external Keycloak/OIDC) shares this one
 * endpoint. Which mechanism runs is decided by which fields the caller populates, checked in
 * this order:
 * <ol>
 *   <li>{@code idToken} present -> Google sign-in ({@link com.heuristiq.uam.google.UamGoogleAuthService})</li>
 *   <li>{@code providerType} present -> external IdP login ({@link com.heuristiq.uam.identity.IdentityProviderAuthRouter})</li>
 *   <li>otherwise -> local username/password (+ optional {@code totpCode} for the 2FA resubmit)</li>
 * </ol>
 * {@code userType} only matters the first time this identity is seen (deciding the role assigned
 * to a brand-new local account created for a Google/external-IdP login) -- irrelevant for local
 * login, which resolves an existing account by username/email/phone regardless of type.
 */
@Schema(description = "Single request shape for every login mechanism (local, Google, external IdP). "
        + "No field is unconditionally required -- which mechanism runs depends on which fields are populated: "
        + "idToken present -> Google; providerType present -> external IdP; otherwise -> local username/password.")
public record UnifiedLoginRequest(
        // Local username/password
        @Schema(description = "Local login username. Required for local username/password login.", example = "jane.doe") String username,
        @Schema(description = "Local login password. Required for local username/password login.") String password,
        @Schema(description = "TOTP code for the local-login MFA resubmit, if the account requires it.") String totpCode,
        // Google
        @Schema(description = "Google ID token. Presence selects Google sign-in.") String idToken,
        // External IdP (Keycloak/OIDC)
        @Schema(description = "External IdP identifier (e.g. \"keycloak\"). Presence selects external-IdP login.") String providerType,
        @Schema(description = "OAuth2 grant type for the external IdP (e.g. \"password\", \"authorization_code\").") String grantType,
        @Schema(description = "OAuth2 authorization code, for the external-IdP authorization_code grant.") String authorizationCode,
        @Schema(description = "OAuth2 redirect URI, for the external-IdP authorization_code grant.") String redirectUri,
        @Schema(description = "TOTP code for the external-IdP MFA resubmit, if required.") String totp,
        // Shared
        @Schema(description = "User type for a brand-new local account created on first Google/external-IdP login. "
                + "Irrelevant for existing-account local login.") UamEnums.UserType userType
) {
}
