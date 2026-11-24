package com.heuristiq.gateway.config;

import java.util.regex.Pattern;

/** Fixed values of the Workflow route. */
public final class WorkflowRouteConstants {

    /**
     * The Workflow service-to-service endpoints ({@code /api/v1/workflow/internal/**}, e.g. the process lookup of
     * Onboarding's readiness check). Matched against the normalised path; the gateway answers 404 for them.
     */
    public static final Pattern INTERNAL_PATH = Pattern.compile("^/api/v\\d+/workflow/internal(/|$)");

    private WorkflowRouteConstants() {
    }
}
