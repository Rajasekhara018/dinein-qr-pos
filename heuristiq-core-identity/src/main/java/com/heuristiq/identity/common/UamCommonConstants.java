package com.heuristiq.identity.common;

/**
 * Fixed values shared by more than one UAM feature, kept in one place so the same literal is never
 * repeated across packages.
 */
public final class UamCommonConstants {

    /** Code of the platform-wide administrator role (the role code stored on users and roles). */
    public static final String SUPER_ADMIN_ROLE = "SUPER_ADMIN";

    /** Spring Security authority carried by a signed-in platform administrator. */
    public static final String SUPER_ADMIN_AUTHORITY = "ROLE_" + SUPER_ADMIN_ROLE;

    /** Request/response header carrying the correlation id (the same header the API gateway sets). */
    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";

    /** MDC key holding the correlation id; also referenced by {@code logging.pattern.level}. */
    public static final String CORRELATION_ID_MDC_KEY = "correlationId";

    /** Digest algorithm used for one-way hashes of tokens, OTPs and device fingerprints. */
    public static final String SHA_256 = "SHA-256";

    /** Shown when a sign-in is attempted on an account that is not active. */
    public static final String USER_NOT_ACTIVE_MESSAGE = "User account is not active";

    /** Bean name of the virtual-thread executor used for outbound, fire-and-forget work. */
    public static final String NOTIFICATION_EXECUTOR = "notificationExecutor";

    /** Attribute of a stored permission holding its catalogue privilege id. */
    public static final String PERMISSION_PRIVILEGE_ID_ATTRIBUTE = "privilegeId";
    public static final String PERMISSION_CATALOG_VERSION_ATTRIBUTE = "privilegeCatalogVersion";
    public static final int PERMISSION_CATALOG_VERSION = 2;

    /** Attribute of a stored permission holding its catalogue API path. */
    public static final String PERMISSION_API_PATH_ATTRIBUTE = "apipath";

    /** Length of a generated temporary password (sign-in requires more than 12 characters). */
    public static final int TEMPORARY_PASSWORD_LENGTH = 14;

    /** Upper-case letters used for generated temporary passwords (no easily confused I and O). */
    public static final String PASSWORD_UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";

    /** Lower-case letters used for generated temporary passwords (no easily confused l). */
    public static final String PASSWORD_LOWER = "abcdefghijkmnopqrstuvwxyz";

    /** Digits used for generated temporary passwords (no easily confused 0 and 1). */
    public static final String PASSWORD_DIGITS = "23456789";

    /** Symbols for temporary passwords that are sent by email or SMS: no &amp;, angle brackets or quotes. */
    public static final String PASSWORD_MESSAGE_SAFE_SYMBOLS = "@#$%*!?";

    private UamCommonConstants() {
    }
}
