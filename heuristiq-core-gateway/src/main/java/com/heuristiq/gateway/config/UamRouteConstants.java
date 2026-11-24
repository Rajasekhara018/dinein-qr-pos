package com.heuristiq.gateway.config;

import java.util.regex.Pattern;

/** Route values of Rider_UAM that the gateway needs beyond the plain path forward. */
public final class UamRouteConstants {

    /**
     * UAM's service-token-only endpoints ({@code /api/v1/uam/internal/**}: application access, self-register, franchise
     * access, org directory). Services reach them directly by base URL, never through the gateway, so the gateway answers 404.
     */
    public static final Pattern INTERNAL_PATH = Pattern.compile("^/api/v\\d+/uam/internal(/|$)");

    /** UAM's actuator endpoints, which are not part of the public API. */
    public static final Pattern ACTUATOR_PATH = Pattern.compile("^/api/v\\d+/uam/actuator(/|$)");

    private UamRouteConstants() {
    }
}
