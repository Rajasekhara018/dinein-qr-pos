package com.heuristiq.uam.organization.subscription;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

@Schema(description = "Gives an organization the use of a registered application.")
public record SubscribeTenantApplicationRequest(
        @Schema(description = "Code of a registered application.", example = "PASUPU_KUMKUMA_MATRIMONY_APP")
        @NotBlank @Size(max = 100, message = "must be at most 100 characters") String applicationCode,
        @Schema(description = "Most members that may use it; leave empty for no limit of its own.", example = "500")
        @PositiveOrZero(message = "must not be negative") Integer maxUsers,
        @Schema(description = "Last day the organization may use it; leave empty for no end date.") LocalDate validUntil,
        @Schema(description = "Optional note, shown to administrators.")
        @Size(max = 500, message = "must be at most 500 characters") String note
) {
}
