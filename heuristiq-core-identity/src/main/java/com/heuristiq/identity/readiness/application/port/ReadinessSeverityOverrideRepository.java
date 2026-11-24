package com.heuristiq.uam.readiness.application.port;

import java.time.Instant;
import java.util.Map;

import com.heuristiq.uam.readiness.domain.ReadinessSeverity;

/**
 * Stored severity overrides of readiness steps, per organization and application. Only differences from the code defaults are
 * stored. Every call names the organization; one organization's rows are never visible to another.
 */
public interface ReadinessSeverityOverrideRepository {

    /** The overrides of one application for the organization (step id to severity); empty when there are none. */
    Map<String, ReadinessSeverity> find(String tenantId, String applicationCode);

    /** The overrides of every application for the organization (application code to step id to severity). */
    Map<String, Map<String, ReadinessSeverity>> findAll(String tenantId);

    /**
     * Makes the stored overrides of the application for the organization exactly {@code overrides}: missing rows are added,
     * changed ones updated, rows not in the map deleted. An empty map clears them.
     *
     * @param userId who made the change (may be null)
     * @param at     when
     */
    void replace(String tenantId, String applicationCode, Map<String, ReadinessSeverity> overrides, String userId, Instant at);
}
