package com.heuristiq.identity.pos.adapter.in;

import com.heuristiq.identity.pos.domain.BranchDraft;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Request body of the branch POST and PUT. Fields are plain text on purpose: they are normalised and checked by the domain
 * validator, which answers with the translatable {@code pos.*} keys.
 */
@Schema(description = "A branch (restaurant location) of a legal entity.")
public record BranchRequest(
        @Schema(description = "The legal entity that owns the branch; cannot change once set") String legalEntityId,
        @Schema(description = "Branch code, unique in the organization: 2 to 20 of A-Z, 0-9 and dash") String code,
        @Schema(description = "Branch name, at most 120 characters") String name,
        @Schema(description = "IANA time zone", example = "Asia/Kolkata") String timeZone,
        @Schema(description = "BCP 47 locale; defaults from the country") String locale,
        @Schema(description = "Two-letter language codes") List<String> languages,
        @Schema(description = "E.164 phone number") String phone,
        String email,
        PosAddressItem address,
        @Schema(description = "Version read with the GET; required on PUT, ignored on POST") Long version) {

    BranchDraft toDraft() {
        return new BranchDraft(legalEntityId, code, name, timeZone, locale, languages, phone, email,
                address == null ? null : address.toDomain());
    }
}
