package com.heuristiq.identity.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Returned by the send-OTP endpoints. {@code otp} is only ever populated when
 * railcab.uam.otp.expose-otp is on (local testing) -- null fields are omitted from the JSON.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Where a one-time passcode was sent and how long it stays valid.")
public record OtpSentResponse(
        @Schema(description = "Masked email address the code was sent to.", example = "ra***@gmail.com")
        String maskedEmail,
        @Schema(description = "Masked phone number the code was sent to (phone OTP only).", example = "******4321")
        String maskedPhone,
        @Schema(description = "Minutes until the code expires.", example = "5")
        Integer expiresInMinutes,
        @Schema(description = "The code itself -- local/test environments only (railcab.uam.otp.expose-otp).")
        String otp
) {
}
