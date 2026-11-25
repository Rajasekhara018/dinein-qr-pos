package com.heuristiq.identity.pos.adapter.in;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Collection;
import java.util.List;

/** Answer of the branch-staff endpoints. */
@Schema(description = "The staff of a branch.")
public record BranchStaffResponse(List<String> userIds) {

    static BranchStaffResponse from(Collection<String> userIds) {
        return new BranchStaffResponse(userIds.stream().sorted().toList());
    }
}
