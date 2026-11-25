package com.heuristiq.identity.readiness.adapter.in;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request bodies of the governance endpoints (inside the commonRequest.requestObject envelope). */
public final class GovernanceRequests {

    private GovernanceRequests() {
    }

    @Schema(description = "Switches an application on although required steps fail. SUPER_ADMIN only.")
    public record GoLiveOverrideRequest(
            @Schema(description = "Why the guard is overridden (length limits are configured; default 10 to 500 characters).")
            @NotBlank(message = "is required") @Size(max = 500, message = "must be at most 500 characters") String reason) {
    }

    @Schema(description = "The owner team of an application for the session organization.")
    public record OwnerTeamRequest(
            @Schema(description = "User ids of department heads (from the owner-team-candidates list). Empty clears the team.")
            @Size(max = 100, message = "must have at most 100 entries") List<@Size(max = 255) String> userIds) {
    }

    @Schema(description = "Sets one readiness step of an application to Required (BLOCK) or Recommended (WARN) for the session organization.")
    public record SeverityRequest(
            @Schema(description = "Readiness step id, e.g. WALLET_APP_ENABLED.", example = "WALLET_APP_ENABLED")
            @NotBlank(message = "is required") @Size(max = 100, message = "must be at most 100 characters") String stepId,
            @Schema(description = "BLOCK (required) or WARN (recommended).", example = "WARN")
            @NotBlank(message = "is required") @Size(max = 10, message = "must be at most 10 characters") String severity) {
    }
}
