package com.heuristiq.uam.mfa;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Completes MFA enrollment by proving control of the second factor (e.g. a TOTP code from an authenticator app).")
public record MfaVerifyRequest(
        @Schema(description = "The current verification code from the authenticator app.", example = "123456") @NotBlank String code
) {
}
