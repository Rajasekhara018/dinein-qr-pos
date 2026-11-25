package com.heuristiq.documents.readiness.domain;

import java.time.Instant;
import java.util.List;

/**
 * The readiness facts this service reports for one application and one organization.
 *
 * @param service         short service name ({@code dms})
 * @param applicationCode the application asked about
 * @param tenantId        the organization asked about
 * @param evaluatedAt     when the checks were evaluated
 * @param checks          one entry per check id, in a fixed order
 */
public record ReadinessReport(String service, String applicationCode, String tenantId, Instant evaluatedAt,
                              List<ReadinessCheck> checks) {

    public ReadinessReport {
        checks = checks == null ? List.of() : List.copyOf(checks);
    }
}
