package com.heuristiq.identity.pos.domain;

import java.util.List;

/** The fields a caller sends to create or change a branch; {@link PosValidator#branch} normalises it. */
public record BranchDraft(String legalEntityId, String code, String name, String timeZone, String locale, List<String> languages,
        String phone, String email, PosAddress address) {

    public BranchDraft {
        languages = languages == null ? List.of() : List.copyOf(languages);
    }
}
