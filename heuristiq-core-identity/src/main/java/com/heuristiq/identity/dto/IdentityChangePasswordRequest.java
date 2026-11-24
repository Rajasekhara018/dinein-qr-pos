package com.heuristiq.uam.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "A signed-in account changes its own password.")
public record IdentityChangePasswordRequest(
        @Schema(description = "The password used to sign in now (the temporary one on a first sign-in).") @NotBlank String oldPassword,
        @Schema(description = "The new password, minimum 8 characters.", example = "S3cureP@ss")
        @NotBlank @Size(min = 8, message = "must be at least 8 characters") String newPassword
) {
}
