package com.heuristiq.identity.organization.membership;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Changes a member's standing in the organization.")
public record UpdateMemberRequest(
        @Schema(description = "ACTIVE, SUSPENDED or REMOVED.") @NotNull TenantMembershipStatus status
) {
}
