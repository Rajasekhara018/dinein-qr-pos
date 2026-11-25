package com.heuristiq.identity.readiness.domain;

import java.util.List;
import java.util.Optional;

/** UAM's own data the readiness combiner reads, always for one organization. */
public interface UamReadinessFactsPort {

    /** Id of the session organization (the token's {@code tenantId}). */
    String sessionOrganizationId();

    /** Codes (upper-case) of the applications linked to the organization and not cancelled. */
    List<String> linkedApplicationCodes(String tenantId);

    /** The application row, or empty when the platform has no row for the code. */
    Optional<ApplicationFacts> application(String applicationCode);

    /** True when the organization has an ACTIVE link to the application that has not passed its end date. */
    boolean activeInOrganization(String tenantId, String applicationCode);

    /**
     * What readiness needs from an application row.
     *
     * @param name   display name
     * @param active whether the application is switched on
     */
    record ApplicationFacts(String name, boolean active) {
    }
}
