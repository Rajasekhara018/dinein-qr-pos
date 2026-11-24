package com.heuristiq.gateway.config;

/**
 * Fixed values of the gateway's request rate limiting: the Redis bucket key prefixes and the login route.
 *
 * <p>The prefixes matter: Spring Cloud Gateway's Redis limiter stores a bucket under the resolved KEY only (not the route), so two
 * limiters that resolve the same key would share one bucket although they have different rates. Each limiter therefore has its own prefix.
 */
public final class RateLimitConstants {

    private RateLimitConstants() {
    }

    /** General API limiter, authenticated caller. */
    public static final String KEY_USER = "user:";
    /** General API limiter, anonymous caller. */
    public static final String KEY_IP = "ip:";
    /** Limiter of the whole auth surface (login, refresh, OTP, reset), per client address. */
    public static final String KEY_AUTH_IP = "auth-ip:";
    /** Limiter of the login call alone, per client address. */
    public static final String KEY_LOGIN_IP = "login-ip:";
    /** One shared bucket for every login call, sized to what UAM can hash per second. */
    public static final String KEY_LOGIN_GLOBAL = "login:global";
    /** Limiter of the public live-trip tracking call, per client address (the caller is never signed in). */
    public static final String KEY_PUBLIC_TRACK_IP = "track-ip:";
    /** Limiter of the token refresh call alone, per client address: mobile apps behind one carrier IP refresh often. */
    public static final String KEY_REFRESH_IP = "refresh-ip:";
    /** Limiter of the public sign-up call alone, per client address, with an hourly budget. */
    public static final String KEY_REGISTER_IP = "register-ip:";
    /** Seconds in the window the sign-up budget is expressed in. */
    public static final int SECONDS_PER_HOUR = 3600;

    public static final String UNKNOWN_ADDRESS = "unknown";
    public static final String HEADER_FORWARDED_FOR = "X-Forwarded-For";

    /** UAM's password login; the only route that gets the two login limiters. */
    public static final String LOGIN_PATH = "/api/v1/uam/identity/auth/login";
    public static final String LOGIN_ROUTE_ID = "uam-login-api";

    /** UAM's token refresh; gets its own, more generous limiter so phones sharing a carrier IP are not logged out by a 429. */
    public static final String REFRESH_PATH = "/api/v1/uam/identity/auth/refresh";
    public static final String REFRESH_ROUTE_ID = "uam-refresh-api";

    /**
     * UAM's public account sign-up; gets an hourly limit per client address on top of the auth surface's limit, because it
     * answers 409 for an existing phone or email (see {@code railcab.gateway.rate-limit.register-per-hour}).
     */
    public static final String REGISTER_PATH = "/api/v1/uam/identity/auth/register";
    public static final String REGISTER_ROUTE_ID = "uam-register-api";
}
