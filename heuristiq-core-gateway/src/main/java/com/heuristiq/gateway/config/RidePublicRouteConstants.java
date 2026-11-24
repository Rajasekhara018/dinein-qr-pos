package com.heuristiq.gateway.config;

/**
 * Route values of the public live-trip tracking call. The share token travels in the {@link #SHARE_TOKEN_HEADER}
 * header (never in the URL), the call needs no sign-in, only GET is public, and it has its own per-address rate limit.
 */
public final class RidePublicRouteConstants {

    /** Public tracking call served by Rider_RideService (no rewrite). */
    public static final String PUBLIC_TRACK_PATH = "/api/v1/ride/public/track";

    public static final String PUBLIC_TRACK_ROUTE_ID = "rider-ride-public-track";

    /**
     * Circuit breaker of the public route only (not the signed-in ride API's {@code rider-ride-cb}), so anonymous polls
     * failing cannot open the breaker every rider and driver call goes through.
     */
    public static final String PUBLIC_TRACK_CIRCUIT_BREAKER = "rider-ride-public-track-cb";

    /** Suffix of its fallback ({@code forward:/fallback/rider-ride-public-track}). */
    public static final String PUBLIC_TRACK_FALLBACK = "rider-ride-public-track";

    /** Header carrying the share token; redacted in audit records by the "token" header rule. */
    public static final String SHARE_TOKEN_HEADER = "X-Share-Token";

    private RidePublicRouteConstants() {
    }
}
