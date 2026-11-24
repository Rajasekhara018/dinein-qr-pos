package com.heuristiq.search.query;

import com.heuristiq.search.model.SearchModel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** Root of a search request: which model to search, and an optional filter group. */
public record SearchQuery(
        @NotNull(message = "model is required") SearchModel model,
        @Valid SearchGroup group) {
}
