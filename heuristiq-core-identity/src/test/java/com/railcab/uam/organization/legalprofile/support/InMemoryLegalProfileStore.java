package com.heuristiq.uam.organization.legalprofile.support;

import com.heuristiq.uam.organization.legalprofile.application.port.LegalProfileStore;
import com.heuristiq.uam.organization.legalprofile.domain.LegalProfile;
import com.heuristiq.uam.organization.legalprofile.domain.LegalProfileException;
import com.heuristiq.uam.organization.legalprofile.domain.StoredLegalProfile;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** A legal-profile store in memory, keyed by organization, with the same version rule as the real stores. */
public class InMemoryLegalProfileStore implements LegalProfileStore {

    private final Map<String, StoredLegalProfile> rows = new HashMap<>();

    @Override
    public Optional<StoredLegalProfile> find(String organizationId) {
        return Optional.ofNullable(rows.get(organizationId));
    }

    @Override
    public StoredLegalProfile save(String organizationId, LegalProfile profile, Long expectedVersion, String userId, Instant at) {
        StoredLegalProfile current = rows.get(organizationId);
        if (!Objects.equals(current == null ? null : current.version(), expectedVersion)) {
            throw LegalProfileException.staleVersion();
        }
        StoredLegalProfile saved = new StoredLegalProfile(profile.withLegalName(null), current == null ? 1L : current.version() + 1, at, userId);
        rows.put(organizationId, saved);
        return saved;
    }

    /** Writes a row as another instance or person would, bypassing the version check. */
    public void putDirectly(String organizationId, StoredLegalProfile stored) {
        rows.put(organizationId, stored);
    }
}
