package com.heuristiq.search.query;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * An AND/OR group: its conditions and any nested sub-groups are combined with {@code logic}. Nesting lets a client
 * express {@code a AND (b OR c)}; a group with neither conditions nor sub-groups is rejected.
 */
public record SearchGroup(
        @NotNull(message = "logic operator (AND/OR) is required") LogicOp logic,
        @Valid List<SearchCondition> conditions,
        @Valid List<SearchGroup> groups) {

    public SearchGroup {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        groups = groups == null ? List.of() : List.copyOf(groups);
    }

    /** A flat group of conditions (no sub-groups). */
    public SearchGroup(LogicOp logic, List<SearchCondition> conditions) {
        this(logic, conditions, List.of());
    }

    /** True when the group holds at least one condition or sub-group. */
    @JsonIgnore
    @AssertTrue(message = "at least one condition or nested group is required")
    public boolean isNotEmpty() {
        return !conditions.isEmpty() || !groups.isEmpty();
    }
}
