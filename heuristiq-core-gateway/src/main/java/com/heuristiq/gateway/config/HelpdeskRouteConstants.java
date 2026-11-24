package com.heuristiq.gateway.config;

/**
 * Route values of the customer and driver help desk (support tickets raised from the apps). Rider_AdminAPI owns the
 * tickets and serves exactly the client-facing path, so nothing is rewritten.
 */
public final class HelpdeskRouteConstants {

    /** What clients call and what Rider_AdminAPI serves: {@code /api/v1/helpdesk/<path>}. */
    public static final String GATEWAY_PREFIX = "/api/v1/helpdesk";

    public static final String ROUTE_ID = "helpdesk-api";

    /** Same backend as the admin route, so the same breaker and fallback. */
    public static final String CIRCUIT_BREAKER = "admin-cb";
    public static final String FALLBACK = "admin";

    /** Path segment after {@code /api/v1/} that the privilege filter maps to the catalog service {@link #CATALOG_SERVICE}. */
    public static final String PATH_SEGMENT = "helpdesk";

    /** Catalog service slug of the help-desk entries in privilege.json (they belong to Rider_AdminAPI). */
    public static final String CATALOG_SERVICE = "admin";

    private HelpdeskRouteConstants() {
    }
}
