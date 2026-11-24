package com.heuristiq.gateway.identity;

/** Fixed values of the gateway's read-only view of UAM users. */
public final class IdentityConstants {

    /**
     * UAM's active user status, as stored (UAM's {@code UamEnums.UserStatus.ACTIVE}); compared as a literal so the
     * gateway does not duplicate UAM's enum.
     */
    public static final String ACTIVE_STATUS = "ACTIVE";

    private IdentityConstants() {
    }
}
