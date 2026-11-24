package com.heuristiq.gateway.config;

import java.util.regex.Pattern;

/**
 * Route values of Rider_AccountingService (the company's general ledger: chart of accounts, trial balance), which
 * serves exactly the client-facing path.
 */
public final class AccountingRouteConstants {

    /** What clients call and what the service serves: {@code /api/v1/accounting/<path>}. */
    public static final String GATEWAY_PREFIX = "/api/v1/accounting";

    /** Prefix of the docs and javadoc routes. */
    public static final String DOCS_PREFIX = "/api/rider/accounting";

    /** Eureka / Simple Discovery service id ({@code spring.application.name} upper-cased). */
    public static final String SERVICE_ID = "REALBOOM-ACCOUNTINGSERVICE";

    /** Local port when neither discovery nor a static URL is configured. */
    public static final int LOCAL_PORT = 9126;

    public static final String ROUTE_ID = "rider-accounting-api";
    public static final String DOCS_ROUTE_ID = "rider-accounting-docs";
    public static final String JAVADOC_ROUTE_ID = "rider-accounting-javadoc";
    public static final String CIRCUIT_BREAKER = "rider-accounting-cb";

    /** Suffix of the circuit-breaker fallback ({@code forward:/fallback/rider-accounting}); also the catalog service slug. */
    public static final String FALLBACK = "rider-accounting";

    /**
     * Any service-token-only endpoints the service may expose ({@code /api/v1/accounting/internal/**}). Matched against
     * the normalised path; the gateway answers 404 for them.
     */
    public static final Pattern INTERNAL_PATH = Pattern.compile("^/api/v\\d+/accounting/internal(/|$)");

    /** The service's actuator endpoints, which are never part of the public API; the gateway answers 404. */
    public static final Pattern ACTUATOR_PATH = Pattern.compile("^/api/v\\d+/accounting/actuator(/|$)");

    private AccountingRouteConstants() {
    }
}
