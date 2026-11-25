package com.heuristiq.identity.application.access;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Asks for a one-time password for an active customer account that has no password yet. Called by an application backend when it activates the account.")
public record IssueTemporaryPasswordRequest(
        @Schema(description = "Id of the account.", example = "6523a1c2e4b0f1a2b3c4d5e6") @NotBlank String userId
) {
}
