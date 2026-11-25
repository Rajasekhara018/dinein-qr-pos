package com.heuristiq.identity.dto;

import com.heuristiq.identity.enums.UamEnums;
import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Completes a forgot-password flow: verifies the code sent by /identity/auth/email/send-otp
 *  and sets the new password in one step, for any UserType. */
@Schema(description = "Completes a forgot-password flow: verifies the emailed OTP and sets a new password in one step.")
public record IdentityResetPasswordRequest(
        @Schema(description = "The email address the OTP was sent to.", example = "jane.doe@example.com")
        @Email(message = "must be a valid email address") @NotBlank String email,
        @Schema(description = "The OTP code received via email.", example = "482913") @NotBlank String otp,
        @Schema(description = "The new password to set, minimum 8 characters.", example = "S3cureP@ss")
        @NotBlank @Size(min = 8, message = "must be at least 8 characters") String newPassword,
        @Schema(description = "The type of user account this reset applies to.") @NotNull UamEnums.UserType userType
) {
}
