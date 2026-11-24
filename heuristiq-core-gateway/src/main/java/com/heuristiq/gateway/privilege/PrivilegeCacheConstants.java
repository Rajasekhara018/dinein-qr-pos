package com.heuristiq.gateway.privilege;

/**
 * Fixed values of the gateway's role-to-privilege cache ({@link RolePrivilegeCacheService}). The TTL and size are
 * configurable ({@code railcab.gateway.cache.role-privilege-*}); these only shape how a cached role is renewed.
 */
public final class PrivilegeCacheConstants {

    /**
     * Share of the TTL after which a role that is still being used is reloaded in the background, so busy roles are
     * renewed before they expire and requests keep getting an answer without waiting on the database.
     */
    public static final int REFRESH_PERCENT_OF_TTL = 75;

    /** Shortest background-reload delay worth having; a shorter TTL simply expires and reloads on the next request. */
    public static final long MIN_REFRESH_MILLIS = 1000L;

    /** Percent base for {@link #REFRESH_PERCENT_OF_TTL}. */
    public static final long PERCENT = 100L;

    private PrivilegeCacheConstants() {
    }
}
