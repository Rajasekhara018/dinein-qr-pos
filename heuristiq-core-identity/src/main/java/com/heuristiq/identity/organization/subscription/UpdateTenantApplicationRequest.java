package com.heuristiq.uam.organization.subscription;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

@Schema(description = "Changes an organization's use of an application. A field left out is not changed.")
public record UpdateTenantApplicationRequest(
        @Schema(description = "ACTIVE, SUSPENDED or CANCELLED.") TenantApplicationStatus status,
        @Schema(description = "Most members that may use it.") @PositiveOrZero(message = "must not be negative") Integer maxUsers,
        @Schema(description = "Last day the organization may use it.") LocalDate validUntil,
        @Schema(description = "Optional note.") @Size(max = 500, message = "must be at most 500 characters") String note
) {
}
