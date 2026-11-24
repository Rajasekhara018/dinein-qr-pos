package com.heuristiq.gateway.common.constants;

public final class MessageConstants {

    private MessageConstants() {}

    public static final String UNAUTHORIZED          = "Unauthorized";
    public static final String ACCESS_TOKEN_MISSING  = "Access token missing";
    public static final String TOKEN_EXPIRED         = "Token has expired";
    public static final String INVALID_TOKEN         = "Invalid token";
    public static final String FORBIDDEN             = "Forbidden";
    public static final String ACCESS_NOT_ALLOWED    = "Access not allowed";
    public static final String BAD_REQUEST           = "Bad Request";
    public static final String ERROR                 = "Error";
    public static final String AUTH_FAILURE          = "Authorization process failure";
    public static final String USER_NOT_FOUND        = "User not found";
    public static final String SERVICE_UNAVAILABLE   = "Service temporarily unavailable";
    /** Error title of the circuit-breaker fallback responses. */
    public static final String SERVICE_UNAVAILABLE_TITLE = "Service Unavailable";
    public static final String REQUEST_TOO_LARGE     = "Request body exceeds the allowed size limit";
    public static final String RATE_LIMIT_EXCEEDED   = "Too many requests. Please slow down and try again.";
}
