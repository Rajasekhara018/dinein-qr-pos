package com.heuristiq.uam.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Self-service password change for the caller's own account. Requires the current password to be supplied and correct.")
public record ChangePasswordRequest(
        @Schema(description = "The caller's current password, verified before applying the change.") @NotBlank String oldPassword,
        @Schema(description = "The new password to set.") @NotBlank String newPassword
) {
}
