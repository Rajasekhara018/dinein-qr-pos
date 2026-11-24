package com.heuristiq.uam.application;

import com.heuristiq.uam.application.catalogue.ApplicationCatalogueEntry;
import com.heuristiq.uam.enums.UamEnums.ApplicationType;

import io.swagger.v3.oas.annotations.media.Schema;

/** One catalogue entry as the create form sees it, with whether the session organization already has it. */
@Schema(description = "An application the organization can create, with its defaults.")
public record ApplicationCatalogueItem(
        @Schema(example = "BESTRIDE_RIDER_APP") String code,
        @Schema(example = "BestRide Rider") String name,
        String description,
        ApplicationType type,
        int displayOrder,
        @Schema(description = "Generic icon name (Material icon).", example = "local_taxi") String iconHint,
        @Schema(description = "True when the session organization already has this application.") boolean created) {

    static ApplicationCatalogueItem of(ApplicationCatalogueEntry entry, boolean created) {
        return new ApplicationCatalogueItem(entry.code(), entry.name(), entry.description(), entry.type(),
                entry.displayOrder(), entry.iconHint(), created);
    }
}
