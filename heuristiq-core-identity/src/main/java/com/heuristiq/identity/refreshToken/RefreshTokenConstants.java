package com.heuristiq.uam.refreshToken;

/**
 * Fixed values of refresh-token rotation: revocation reasons stored on a token, the error code a failed refresh
 * returns, and the bounds of the reuse-grace walk. Durations that may differ per deployment live in
 * {@link com.heuristiq.uam.refreshToken.config.RefreshTokenProperties}, not here.
 */
public final class RefreshTokenConstants {

    /** Reason on a token that was redeemed and replaced by its successor. */
    public static final String REASON_ROTATED = "ROTATED";

    /** Reason on a token revoked by an administrator, by logout or by "revoke all". */
    public static final String REASON_REVOKED_BY_ADMIN = "REVOKED_BY_ADMIN";

    /** Reason on every token of a family revoked because an already-rotated token was presented again (likely theft). */
    public static final String REASON_REUSE_DETECTED = "REUSE_DETECTED";

    /** Reason on a successor that was stored by a rotation which then lost the race to a concurrent one; never handed out. */
    public static final String REASON_ROTATION_LOST = "ROTATION_LOST";

    /** Machine-readable code of a refused refresh: the client must sign in again. */
    public static final String ERROR_REFRESH_TOKEN_INVALID = "REFRESH_TOKEN_INVALID";

    /** Message of a refused refresh (deliberately the same for unknown, expired, revoked and reused tokens). */
    public static final String INVALID_REFRESH_TOKEN_MESSAGE = "Invalid or expired refresh token";

    /** Most successor links followed when a grace-window replay resolves the family's current token. */
    public static final int MAX_SUCCESSOR_WALK = 10;

    /** Domain-separation label used to derive the successor-derivation key from the signing secret. */
    public static final String SUCCESSOR_KEY_LABEL = "railcab-uam/refresh-token-successor/v1";

    /** MAC algorithm used for successor derivation. */
    public static final String HMAC_ALGORITHM = "HmacSHA256";

    /** Separator between the presented token and the successor id in the derivation input. */
    public static final char DERIVATION_SEPARATOR = '|';

    private RefreshTokenConstants() {
    }
}
