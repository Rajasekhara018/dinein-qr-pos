package com.heuristiq.gateway.config;

import java.util.regex.Pattern;

/** Fixed values of the DMS route. */
public final class DmsRouteConstants {

    /**
     * The DMS service-to-service endpoints ({@code /api/v1/dms/internal/**}, e.g. the Workflow callback that applies an
     * approved document type change). Matched against the normalised path; the gateway answers 404 for them.
     */
    public static final Pattern INTERNAL_PATH = Pattern.compile("^/api/v\\d+/dms/internal(/|$)");

    private DmsRouteConstants() {
    }
}
