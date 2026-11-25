package com.heuristiq.identity.readiness.application;

import com.heuristiq.identity.readiness.domain.ApplicationReadiness;

/**
 * Lets an admin choose, for the session organization and one application, which setup steps are Required (BLOCK) and which
 * only Recommended (WARN). Rules: {@link com.heuristiq.identity.readiness.domain.ReadinessSeverityRules}. Every call returns the
 * fresh readiness of the application.
 */
public interface ReadinessSeverityUseCase {

    /**
     * Sets one step's severity (the dependency rule may change other steps too).
     *
     * @param severity {@code BLOCK} or {@code WARN} (any case)
     * @throws com.heuristiq.identity.readiness.domain.ReadinessSeverityException 400 invalid severity, 404 unknown step, 409 locked step
     * @throws com.heuristiq.identity.common.UamException 404 when the application is not linked to the session organization
     */
    ApplicationReadiness change(String applicationCode, String stepId, String severity);

    /** Sets one step back to its default (and the steps coupled to it, see the rules). Same errors as {@link #change}. */
    ApplicationReadiness reset(String applicationCode, String stepId);

    /** Sets every step of the application back to its default for the session organization. */
    ApplicationReadiness resetAll(String applicationCode);
}
