package com.heuristiq.identity.readiness.domain;

/**
 * Asks one service for its readiness facts about one application in one organization
 * ({@code GET <service internal base>/readiness?applicationCode=&tenantId=}).
 */
public interface ServiceReadinessPort {

    /**
     * Never throws for a service problem: a down, slow, refusing or confused service gives a report whose outcome says so,
     * and the evaluator turns every check of that service into UNKNOWN.
     *
     * @param source          the service to ask (never {@link ReadinessSource#UAM})
     * @param applicationCode catalogue code
     * @param tenantId        the session organization's id
     */
    ServiceReport fetch(ReadinessSource source, String applicationCode, String tenantId);
}
