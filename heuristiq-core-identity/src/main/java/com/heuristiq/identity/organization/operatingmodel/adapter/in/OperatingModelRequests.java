package com.heuristiq.uam.organization.operatingmodel.adapter.in;

import com.heuristiq.uam.organization.operatingmodel.domain.OperatingModel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/** Request bodies of the operating-model endpoints. */
public final class OperatingModelRequests {

    private OperatingModelRequests() {
    }

    @Schema(description = "One scheduled change of operating model.")
    public record ScheduledChange(
            @Schema(description = "The model from that day: DIRECT, FRANCHISE or HYBRID.", example = "FRANCHISE") @NotNull OperatingModel model,
            @Schema(description = "First day of the new model, in the organization's time zone; today or later.", example = "2026-12-01")
            @NotNull LocalDate effectiveFrom) {
    }

    @Schema(description = "Every change that has not started yet, oldest first. Replaces the scheduled changes; an empty list cancels them. "
            + "Changes already in force are kept.")
    public record ScheduleOperatingModelRequest(
            @Schema(description = "Scheduled changes, oldest first; at most 20.") @NotNull @Size(max = 20)
            List<@Valid @NotNull ScheduledChange> scheduledChanges) {
    }
}
