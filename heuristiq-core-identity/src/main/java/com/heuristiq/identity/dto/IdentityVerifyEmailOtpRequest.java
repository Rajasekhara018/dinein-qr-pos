package com.heuristiq.uam.dto;

import com.heuristiq.uam.enums.UamEnums;
import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Verifies an email OTP previously sent via /identity/auth/email/send-otp.")
public record IdentityVerifyEmailOtpRequest(
        @Schema(description = "The email address the OTP was sent to.", example = "jane.doe@example.com")
        @Email(message = "must be a valid email address") @NotBlank String email,
        @Schema(description = "The OTP code received via email.", example = "482913") @NotBlank String otp,
        @Schema(description = "The type of user account this verification applies to.") @NotNull UamEnums.UserType userType,
        @Schema(description = "Authenticator code; required only for accounts with MFA enrolled (resubmit after an MfaChallengeRequiredResponse).") String totpCode
) {
}
