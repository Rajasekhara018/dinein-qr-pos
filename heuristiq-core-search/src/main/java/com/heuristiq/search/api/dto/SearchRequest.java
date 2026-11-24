package com.heuristiq.search.api.dto;

import com.heuristiq.search.query.SearchQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

@Schema(description = "A paged search request: which model to search plus an optional AND/OR filter group.")
public record SearchRequest(
        @Schema(description = "Model to search, and the filter conditions to apply")
        @NotNull(message = "query is required") @Valid SearchQuery query,

        @Schema(description = "Zero-based page index", example = "0")
        @PositiveOrZero(message = "page must be zero or greater") int page,

        @Schema(description = "Page size", example = "20")
        @Max(value = 200, message = "size must not exceed 200") int size) {
}
