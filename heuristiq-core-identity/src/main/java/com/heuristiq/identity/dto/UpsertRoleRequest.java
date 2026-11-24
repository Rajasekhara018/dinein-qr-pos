package com.heuristiq.uam.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

import java.util.Set;

@Schema(description = "Create/update payload for a role.")
public record UpsertRoleRequest(
        @Schema(description = "Unique role code.", example = "ADMIN") @NotBlank String code,
        @Schema(description = "Display name.", example = "Administrator") String name,
        @Schema(description = "Free-text description.") String description,
        @Schema(description = "Permission codes granted by this role.") Set<String> permissions,
        @Schema(description = "Whether this is a built-in system role (typically immutable/protected).", example = "false") boolean systemRole
) {
}
