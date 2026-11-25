package com.heuristiq.identity.readiness.domain;

/**
 * Overall readiness of one application for one organization. The rules are in {@link ReadinessEvaluator}.
 */
public enum OverallReadinessStatus {
    /** Every blocking step passed (warnings may remain, see {@code warningCount}). */
    READY,
    /** No blocking step failed, but some blocking steps could not be evaluated yet. */
    NEEDS_SETUP,
    /** At least one blocking step failed. */
    BLOCKED,
    /** Most of the services the application relies on did not answer, so the picture is unreliable. */
    UNKNOWN
}
