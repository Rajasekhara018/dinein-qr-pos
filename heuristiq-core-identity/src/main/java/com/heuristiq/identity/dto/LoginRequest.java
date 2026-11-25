package com.heuristiq.identity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Local username/password login credentials, resolved internally from UnifiedLoginRequest.")
public record LoginRequest(
        @Schema(description = "Account username.", example = "jane.doe") @NotBlank String username,
        @Schema(description = "Account password.") @NotBlank String password,
        // Optional — only required on the resubmit after the server returns MfaChallengeRequiredResponse.
        @Schema(description = "TOTP code, required only on the resubmit after an MfaChallengeRequiredResponse.") String totpCode,
        // Optional: a customer app names the self-service type it signs in (CUSTOMER), so a back-office account is refused there.
        @Schema(description = "User type the client signs in as (customer apps send CUSTOMER); none for the staff portals.")
        com.heuristiq.identity.enums.UamEnums.UserType userType
) {
    /** A sign-in that names no user type (the staff portals and older clients). */
    public LoginRequest(String username, String password, String totpCode) {
        this(username, password, totpCode, null);
    }
}
