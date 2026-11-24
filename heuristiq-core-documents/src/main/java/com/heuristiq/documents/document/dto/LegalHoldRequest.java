package com.railcab.dms.document.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Request to set or clear legal hold on a document.")
public record LegalHoldRequest(
        @Schema(description = "True to place the document under legal hold (blocks deletion); false to clear it.", example = "true")
        boolean enabled
) {
}
