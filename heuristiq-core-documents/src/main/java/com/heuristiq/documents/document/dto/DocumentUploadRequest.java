package com.heuristiq.documents.document.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Category/owner filter used by the request-object search endpoint (also doubles as the shape " +
        "read for category/ownerId on upload-adjacent flows).")
public record DocumentUploadRequest(
        @Schema(description = "Logical document category to filter by.", example = "invoices")
        @NotBlank(message = "category is required") String category,
        @Schema(description = "Owner id to filter by.", example = "user-12345")
        @NotBlank(message = "ownerId is required") String ownerId
) {
}
