package com.heuristiq.gateway.config;

/**
 * Fixed wire values shared by every route in {@link RouteConfig}: the headers the gateway adds to forwarded requests,
 * the CORS response-header dedupe, the circuit-breaker fallback prefix and the URI schemes a route target is built from.
 */
public final class RouteHeaderConstants {

    /** Header naming the gateway as the forwarder, for downstream audit. */
    public static final String FORWARDED_BY_HEADER = "X-Forwarded-By";
    public static final String FORWARDED_BY_VALUE = "railcab-gateway";

    /** Public gateway prefix of the route, so springdoc builds externally reachable server URLs. */
    public static final String FORWARDED_PREFIX_HEADER = "X-Forwarded-Prefix";

    /** API key header NMS expects on every request the gateway forwards to it. */
    public static final String NMS_API_KEY_HEADER = "X-NMS-API-Key";

    /** Response headers deduplicated on every resilient route (space-separated, as the filter expects). */
    public static final String CORS_DEDUPE_HEADERS = "Access-Control-Allow-Origin Access-Control-Allow-Credentials";
    public static final String CORS_DEDUPE_STRATEGY = "RETAIN_FIRST";

    /** Circuit-breaker fallback target prefix; the route's fallback slug is appended. */
    public static final String FALLBACK_URI_PREFIX = "forward:/fallback/";

    /** Load-balanced (service registry) HTTP scheme. */
    public static final String LB_SCHEME = "lb://";
    /** Load-balanced WebSocket scheme. */
    public static final String LB_WS_SCHEME = "lb:ws://";
    /** Base of a local fallback URL; the service's local port is appended. */
    public static final String LOCAL_HTTP_PREFIX = "http://localhost:";

    private RouteHeaderConstants() {
    }
}
