package com.heuristiq.uam.organization.legalprofile.application.port;

import java.util.Optional;

/**
 * The organizations whose legal profile is kept, and their legal name, which belongs to the organization record (one owner of
 * the fact): the profile reads and writes it there rather than keeping a second copy.
 */
public interface LegalOrganizationDirectory {

    record Organization(String id, String legalName) {
    }

    Optional<Organization> find(String organizationId);

    /** Changes only the organization's legal name; every other field of the organization stays as it is. */
    void changeLegalName(String organizationId, String legalName);
}
