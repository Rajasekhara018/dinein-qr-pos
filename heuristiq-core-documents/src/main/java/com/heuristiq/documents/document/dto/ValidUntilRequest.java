package com.heuristiq.documents.document.dto;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** A reviewer confirms or corrects the last day a document is valid (D12). */
@Schema(description = "The last day the document is valid, read from the document.")
public record ValidUntilRequest(
        @Schema(description = "ISO date (yyyy-MM-dd).", example = "2027-03-31") @NotNull LocalDate validUntil) {
}
