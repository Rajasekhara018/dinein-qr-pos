package com.heuristiq.identity.organization.operatingmodel.application.port;

import java.util.Optional;

/** Looks up an organization: whether it exists and its own time zone (which decides the organization's "today"). */
public interface OrganizationDirectory {

    record Organization(String id, String timeZone) {
    }

    Optional<Organization> find(String organizationId);
}
