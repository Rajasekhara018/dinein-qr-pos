package com.heuristiq.uam.refreshToken;

import com.heuristiq.uam.common.UamException;
import org.springframework.http.HttpStatus;

/** The one failure every refused refresh returns: 401 with {@code errorCode = REFRESH_TOKEN_INVALID} ("sign in again"). */
public final class RefreshTokenFailures {

    private RefreshTokenFailures() {
    }

    /** Unknown, expired, revoked or reused token: same status, code and message so a caller learns nothing more. */
    public static UamException invalid() {
        return new UamException(RefreshTokenConstants.INVALID_REFRESH_TOKEN_MESSAGE, HttpStatus.UNAUTHORIZED,
                RefreshTokenConstants.ERROR_REFRESH_TOKEN_INVALID);
    }
}
