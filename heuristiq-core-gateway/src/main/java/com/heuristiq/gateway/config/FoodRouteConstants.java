package com.heuristiq.gateway.config;

/**
 * Route values of the food delivery backend (food-delivery-platform/backend), which serves exactly the client-facing path. The
 * whole food API ({@code /api/v1/food/**}) goes through this gateway: the admin API ({@code /api/v1/food/admin/**}, called by the
 * food console inside Rider_Web) is enforced by its privileges (privilege.json, service "food"); the customer, partner and
 * driver APIs need a signed-in UAM user, except the public browse paths of the path matchers.
 */
public final class FoodRouteConstants {

    /** Everything the food backend serves: {@code /api/v1/food/<path>}. */
    public static final String API_PREFIX = "/api/v1/food";
    /** What the food console calls: {@code /api/v1/food/admin/<path>}, the paths of the "food" privileges. */
    public static final String GATEWAY_PREFIX = API_PREFIX + "/admin";
    /** The live order updates (WebSocket). */
    public static final String WEBSOCKET_PATH = API_PREFIX + "/ws/orders";
    /** Path segment of the food API, also the catalog service slug of its privileges. */
    public static final String PATH_SEGMENT = "food";
    /**
     * Local URL when no static URL is configured: the backend's default port. The food backend does not register with the
     * service registry, so there is no discovery id; deployments set {@code FOOD_SERVICE_URL}.
     */
    public static final String LOCAL_URL = "http://localhost:9123";
    public static final String ROUTE_ID = "food-api";
    public static final String WEBSOCKET_ROUTE_ID = "food-websocket";
    public static final String CIRCUIT_BREAKER = "food-cb";
    /** Suffix of the circuit-breaker fallback ({@code forward:/fallback/food}). */
    public static final String FALLBACK = "food";

    private FoodRouteConstants() {
    }
}
