package com.heuristiq.uam.organization.legalprofile.application.port;

import com.heuristiq.uam.organization.legalprofile.domain.LegalProfile;
import com.heuristiq.uam.organization.legalprofile.domain.StoredLegalProfile;
import java.time.Instant;
import java.util.Optional;

/**
 * The saved legal profile of each organization (everything but the legal name, which is on the organization record). Every
 * read and write is scoped to the one organization it names.
 */
public interface LegalProfileStore {

    /** The organization's saved profile; empty when it has never saved one. */
    Optional<StoredLegalProfile> find(String organizationId);

    /**
     * Replaces the organization's profile (tax ids and addresses as whole sets), but only if the stored version is still
     * {@code expectedVersion} (null: nothing is stored yet). Otherwise nothing is written and a {@code LegalProfileException}
     * of kind CONFLICT ({@code legalProfile.staleVersion}) is thrown. The legal name of {@code profile} is not stored here.
     *
     * @return what is now stored, with the new version (1 for the first save)
     */
    StoredLegalProfile save(String organizationId, LegalProfile profile, Long expectedVersion, String userId, Instant at);
}
