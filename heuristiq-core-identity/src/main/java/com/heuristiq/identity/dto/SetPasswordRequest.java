package com.heuristiq.identity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Admin-set password for a user account (PATCH /users/{id}/password).")
public record SetPasswordRequest(
        @Schema(description = "The new password.") @NotBlank String password,
        @Schema(description = "Whether the user must change this password on next login.", example = "true") Boolean temporary
) {
}
