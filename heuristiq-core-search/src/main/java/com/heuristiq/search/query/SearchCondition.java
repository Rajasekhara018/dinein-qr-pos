package com.heuristiq.search.query;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * One condition inside a {@link SearchGroup}, e.g. {@code text3 EQ "COMPLETED"}. {@code field}
 * must be one of {@link com.heuristiq.search.document.SearchDocument}'s generic slot names
 * (text1..text15 / amount1..amount5 / number1..number5 / date1..date5 / datetime1..datetime5) --
 * the prefix determines how {@code value} is parsed and which operators are valid.
 */
public record SearchCondition(
        @NotBlank(message = "field is required") String field,
        @NotNull(message = "operator is required") Operator operator,
        String value) {
}
