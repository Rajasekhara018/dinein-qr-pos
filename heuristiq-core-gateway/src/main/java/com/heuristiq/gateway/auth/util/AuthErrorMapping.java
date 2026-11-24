package com.heuristiq.gateway.auth.util;

import com.heuristiq.gateway.common.constants.MessageConstants;
import org.springframework.http.HttpStatus;

import java.util.Map;

public final class AuthErrorMapping {

    private AuthErrorMapping() {}

    public record ErrorDetails(HttpStatus status, String error, String message) {}

    private static final Map<Integer, ErrorDetails> ERROR_MAP = Map.of(
            HttpStatus.UNAUTHORIZED.value(),
            new ErrorDetails(HttpStatus.UNAUTHORIZED, MessageConstants.UNAUTHORIZED, MessageConstants.INVALID_TOKEN),

            HttpStatus.FORBIDDEN.value(),
            new ErrorDetails(HttpStatus.FORBIDDEN, MessageConstants.FORBIDDEN, MessageConstants.ACCESS_NOT_ALLOWED),

            HttpStatus.BAD_REQUEST.value(),
            new ErrorDetails(HttpStatus.BAD_REQUEST, MessageConstants.BAD_REQUEST, MessageConstants.AUTH_FAILURE)
    );

    public static ErrorDetails get(int statusCode) {
        return ERROR_MAP.getOrDefault(statusCode,
                new ErrorDetails(HttpStatus.INTERNAL_SERVER_ERROR, MessageConstants.ERROR, MessageConstants.AUTH_FAILURE));
    }
}
