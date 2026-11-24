package com.heuristiq.uam.application.access;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Grants a user, or everyone holding a role, access to sign in to a registered application.")
public record GrantApplicationAccessRequest(
        @Schema(description = "Code of a registered application.", example = "PASUPU_KUMKUMA_MATRIMONY_APP") @NotBlank String applicationCode,
        @Schema(description = "USER for a single account, ROLE for everyone holding a role.") @NotNull AccessSubjectType subjectType,
        @Schema(description = "User id for USER, role name for ROLE.", example = "AGENT") @NotBlank @Size(max = 100) String subjectId,
        @Schema(description = "Optional reason, shown to administrators.") @Size(max = 500) String note
) {
}
