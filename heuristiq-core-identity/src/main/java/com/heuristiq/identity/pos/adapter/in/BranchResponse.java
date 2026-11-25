package com.heuristiq.identity.pos.adapter.in;

import com.heuristiq.identity.pos.domain.Branch;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/** Answer of the branch endpoints. */
@Schema(description = "A branch of an organization.")
public record BranchResponse(
        String id,
        String legalEntityId,
        String code,
        String name,
        String timeZone,
        String locale,
        List<String> languages,
        String phone,
        String email,
        PosAddressItem address,
        String status,
        @Schema(description = "Send it back with the PUT") long version,
        Instant updatedAt,
        String updatedBy) {

    static BranchResponse from(Branch b) {
        return new BranchResponse(b.id(), b.legalEntityId(), b.code(), b.name(), b.timeZone(), b.locale(), b.languages(), b.phone(),
                b.email(), PosAddressItem.from(b.address()), b.status().name(), b.version(), b.updatedAt(), b.updatedBy());
    }
}
