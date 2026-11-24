package com.heuristiq.uam.dto;

import com.heuristiq.uam.enums.UamEnums;
import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import com.heuristiq.phone.validation.ValidPhone;

@Schema(description = "Verifies an SMS OTP previously sent via /identity/auth/phone/send-otp.")
public record IdentityVerifyPhoneOtpRequest(
        @Schema(description = "The mobile number the OTP was sent to.", example = "9876543210")
        @NotBlank @ValidPhone String phone,
        @Schema(description = "The OTP code received via SMS.", example = "482913") @NotBlank String otp,
        @Schema(description = "The type of user account this verification applies to.") @NotNull UamEnums.UserType userType,
        @Schema(description = "Authenticator code; required only for accounts with MFA enrolled (resubmit after an MfaChallengeRequiredResponse).") String totpCode
) {
}
