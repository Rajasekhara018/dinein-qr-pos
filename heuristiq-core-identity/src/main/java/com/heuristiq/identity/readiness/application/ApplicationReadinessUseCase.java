package com.heuristiq.uam.readiness.application;

import java.util.List;

import com.heuristiq.uam.readiness.domain.ApplicationReadiness;

/** Readiness of the session organization's applications (docs/APPLICATION_READINESS.md, stage 1b). */
public interface ApplicationReadinessUseCase {

    /**
     * The full readiness guide of one application of the session organization.
     *
     * @param applicationCode catalogue code (any case)
     * @param refresh         true re-checks now instead of reusing a recent result
     * @throws com.heuristiq.uam.common.UamException 404 when the code is not linked to the session organization
     */
    ApplicationReadiness readiness(String applicationCode, boolean refresh);

    /**
     * A summary line for every catalogue application linked to the session organization, in catalogue order.
     *
     * @param refresh true re-checks now instead of reusing recent results
     */
    List<ReadinessSummary> summaries(boolean refresh);

    /**
     * Evaluates one application for an explicitly named organization, bypassing the cache (and refreshing it). For work that
     * has no signed-in caller (the go-live guard, the scheduled re-check). The application only needs a readiness profile;
     * it does not have to be ACTIVE, because the guard evaluates a link that is about to be switched on.
     *
     * @throws com.heuristiq.uam.common.UamException 404 when the code has no readiness profile
     */
    ApplicationReadiness evaluateFor(String tenantId, String applicationCode);

    /** Forgets the cached result of the organization and application (its severity overrides changed). Default: nothing cached. */
    default void invalidate(String tenantId, String applicationCode) {
    }
}
