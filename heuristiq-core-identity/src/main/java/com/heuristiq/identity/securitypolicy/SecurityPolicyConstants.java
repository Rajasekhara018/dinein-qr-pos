package com.heuristiq.identity.securitypolicy;

/** Fixed limits of the values a security policy may hold. */
public final class SecurityPolicyConstants {

    /** Shortest password a policy may require, and the platform floor for every account. */
    public static final int MIN_PASSWORD_LENGTH_FLOOR = 8;

    /** Longest password a policy may require or allow (also the longest a user may set). */
    public static final int PASSWORD_LENGTH_CEILING = 128;

    /** Fewest and most failed sign-ins a policy may allow before locking the account. */
    public static final int MAX_FAILED_ATTEMPTS_MIN = 1;
    public static final int MAX_FAILED_ATTEMPTS_MAX = 20;

    /** Shortest idle or absolute session timeout, in minutes. */
    public static final int MIN_SESSION_TIMEOUT_MINUTES = 1;

    private SecurityPolicyConstants() {
    }
}
