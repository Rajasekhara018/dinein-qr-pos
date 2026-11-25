package com.heuristiq.identity.pos.adapter.in;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Request body of the branch-staff PUT: the complete list of the branch's staff. */
@Schema(description = "The complete staff of a branch; replaces what is stored.")
public record BranchStaffRequest(
        @Schema(description = "Ids of the organization's active members who work at the branch") List<String> userIds) {

    Set<String> toUserIds() {
        return userIds == null ? Set.of() : new LinkedHashSet<>(userIds);
    }
}
