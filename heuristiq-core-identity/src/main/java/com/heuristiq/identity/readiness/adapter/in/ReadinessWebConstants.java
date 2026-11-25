package com.heuristiq.identity.readiness.adapter.in;

import com.heuristiq.identity.application.ApplicationConstants;

/** Paths and parameters of the readiness endpoints. */
public final class ReadinessWebConstants {

    /** Base path, shared with the applications endpoints. */
    public static final String BASE_PATH = ApplicationConstants.BASE_PATH;

    /** Summary of every application of the session organization (privilege APPLICATION_READINESS_LIST). */
    public static final String SUMMARY_PATH = "/readiness";

    /** Full guide of one application (privilege APPLICATION_READINESS_READ). */
    public static final String APPLICATION_PATH = "/{code}/readiness";

    /** SUPER_ADMIN go-live override (privilege APPLICATION_GO_LIVE_OVERRIDE_CREATE). */
    public static final String GO_LIVE_OVERRIDE_PATH = "/{code}/go-live-override";

    /** Owner team of one application (privilege APPLICATION_OWNER_TEAM_UPDATE). */
    public static final String OWNER_TEAM_PATH = "/{code}/owner-team";

    /** Department heads selectable as owners (privilege APPLICATION_OWNER_TEAM_CANDIDATE_LIST). */
    public static final String OWNER_CANDIDATES_PATH = "/owner-team-candidates";

    /** Owner team, override and alert state (privilege APPLICATION_READINESS_GOVERNANCE_READ). */
    public static final String GOVERNANCE_PATH = "/{code}/readiness-governance";

    /** Choose Required/Recommended per step (PUT: APPLICATION_READINESS_SEVERITY_UPDATE; DELETE all: APPLICATION_READINESS_SEVERITY_ALL_DELETE). */
    public static final String SEVERITY_PATH = "/{code}/readiness-severity";

    /** Reset one step to its default (privilege APPLICATION_READINESS_SEVERITY_DELETE). */
    public static final String SEVERITY_STEP_PATH = "/{code}/readiness-severity/{stepId}";

    /** Query parameter: re-check now instead of reusing a recent result. */
    public static final String PARAM_REFRESH = "refresh";

    private ReadinessWebConstants() {
    }
}
