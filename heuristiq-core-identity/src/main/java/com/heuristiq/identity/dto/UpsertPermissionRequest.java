package com.heuristiq.uam.dto;

import com.heuristiq.uam.enums.UamEnums.ModuleCategory;
import com.heuristiq.uam.enums.UamEnums.PermissionAction;
import com.heuristiq.uam.enums.UamEnums.ResourceType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Create/update payload for a permission.")
public record UpsertPermissionRequest(
        @Schema(description = "Unique permission code.", example = "USER_WRITE") @NotBlank String code,
        @Schema(description = "Code of the application this permission belongs to, if application-scoped.") String applicationCode,
        @Schema(description = "Functional module this permission governs.") ModuleCategory module,
        @Schema(description = "Type of resource this permission governs.") ResourceType resource,
        @Schema(description = "Action this permission grants.") PermissionAction action,
        @Schema(description = "Human-readable name.", example = "Manage Users") String displayName,
        @Schema(description = "Free-text description.") String description
) {
}
