package com.heuristiq.uam.selfregister;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request bodies (inside {@code requestObject}) of the self-registration calls. */
public final class SelfRegisterRequests {

    private SelfRegisterRequests() {
    }

    @Schema(description = "Asks for a verification code to be emailed to someone who is creating an account.")
    public record SendOtp(
            @Schema(description = "The address to verify.", example = "jane.doe@example.com")
            @Email(message = "must be a valid email address") @NotBlank @Size(max = 254) String email,
            @Schema(description = "Code of the application the account is for.", example = "PASUPU_KUMKUMA_MATRIMONY_APP")
            @NotBlank @Size(max = 64) String applicationCode,
            @Schema(description = "What the bot-check widget produced, when one is enabled.") @Size(max = 4096) String botToken) {
    }

    @Schema(description = "Checks the code that was emailed.")
    public record VerifyOtp(
            @Schema(description = "The address the code was sent to.") @Email(message = "must be a valid email address") @NotBlank @Size(max = 254) String email,
            @Schema(description = "The six-digit code.", example = "123456") @NotBlank @Size(max = 12) String otp,
            @Schema(description = "Code of the application the account is for.") @NotBlank @Size(max = 64) String applicationCode) {
    }

    @Schema(description = "Service-to-service: spends a verification token.")
    public record ConsumeToken(
            @Schema(description = "The verified address.") @Email(message = "must be a valid email address") @NotBlank @Size(max = 254) String email,
            @Schema(description = "The token returned by verify-otp.") @NotBlank @Size(max = 256) String token,
            @Schema(description = "Code of the application the account is for.") @NotBlank @Size(max = 64) String applicationCode) {
    }

    @Schema(description = "Service-to-service: asks for the set-your-password code to be emailed to a newly created member.")
    public record SendPasswordCode(
            @Schema(description = "The member's email address.") @Email(message = "must be a valid email address") @NotBlank @Size(max = 254) String email) {
    }

    /** @param verified true exactly once for a valid token */
    public record Consumed(boolean verified) {
    }
}
