package com.heuristiq.gateway.config;

import java.util.regex.Pattern;

/** Route values of Rider_FranchiseService (franchises, plans, agents), which serves exactly the client-facing path. */
public final class FranchiseRouteConstants {

    /** What clients call and what the service serves: {@code /api/v1/franchise/<path>}. */
    public static final String GATEWAY_PREFIX = "/api/v1/franchise";

    /** Prefix of the docs and javadoc routes. */
    public static final String DOCS_PREFIX = "/api/rider/franchise";

    /** Eureka / Simple Discovery service id ({@code spring.application.name} upper-cased). */
    public static final String SERVICE_ID = "REALBOOM-FRANCHISESERVICE";

    /** Local port when neither discovery nor a static URL is configured. */
    public static final int LOCAL_PORT = 9124;

    public static final String ROUTE_ID = "rider-franchise-api";
    public static final String DOCS_ROUTE_ID = "rider-franchise-docs";
    public static final String JAVADOC_ROUTE_ID = "rider-franchise-javadoc";
    public static final String CIRCUIT_BREAKER = "rider-franchise-cb";

    /** Suffix of the circuit-breaker fallback ({@code forward:/fallback/franchise}); also the catalog service slug. */
    public static final String FALLBACK = "franchise";

    /**
     * The service's service-token-only endpoints ({@code /api/v1/franchise/internal/**}: by-code lookup and the
     * franchise claim). Matched against the normalised path; the gateway answers 404 for them.
     */
    public static final Pattern INTERNAL_PATH = Pattern.compile("^/api/v\\d+/franchise/internal(/|$)");

    /** The service's actuator endpoints, which are never part of the public API; the gateway answers 404. */
    public static final Pattern ACTUATOR_PATH = Pattern.compile("^/api/v\\d+/franchise/actuator(/|$)");

    private FranchiseRouteConstants() {
    }
}
