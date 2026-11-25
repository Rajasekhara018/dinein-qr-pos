package com.heuristiq.identity.pos.domain;

import java.time.Instant;
import java.util.List;

/** A stored branch (a restaurant location) of a legal entity. */
public record Branch(String id, String tenantId, String legalEntityId, String code, String name, String timeZone, String locale,
        List<String> languages, String phone, String email, PosAddress address, PosStatus status, long version, Instant updatedAt,
        String updatedBy) {

    public Branch {
        languages = languages == null ? List.of() : List.copyOf(languages);
    }
}
