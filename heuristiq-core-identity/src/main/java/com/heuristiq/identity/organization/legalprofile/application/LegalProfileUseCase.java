package com.heuristiq.uam.organization.legalprofile.application;

import com.heuristiq.uam.organization.legalprofile.domain.LegalProfile;
import com.heuristiq.uam.organization.legalprofile.domain.LegalProfileDraft;
import java.time.Instant;

/** Reads and replaces an organization's legal profile (legal entity, tax ids, addresses). */
public interface LegalProfileUseCase {

    /**
     * The profile with its version; {@code version}, {@code updatedAt} and {@code updatedBy} are null when nothing has been
     * saved yet (the profile is then empty apart from the organization's legal name).
     */
    record LegalProfileView(String organizationId, LegalProfile profile, Long version, Instant updatedAt, String updatedBy) {
    }

    /** The organization's profile; never fails for an organization without one. */
    LegalProfileView view(String organizationId);

    /**
     * Replaces the whole profile. {@code expectedVersion} is the version the caller read (null for the first save); a
     * mismatch is refused as stale. The legal name is written to the organization record.
     */
    LegalProfileView replace(String organizationId, LegalProfileDraft draft, Long expectedVersion, String actorUserId);
}
