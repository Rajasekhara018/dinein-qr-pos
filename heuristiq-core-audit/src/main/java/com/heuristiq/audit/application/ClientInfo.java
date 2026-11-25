package com.heuristiq.audit.application;

/**
 * Request metadata attached to a screen view by the service (the client cannot set these).
 *
 * @param correlationId {@code X-Correlation-Id} of the request
 * @param requestId     {@code X-Request-Id} of the request
 * @param ipAddress     client address
 * @param userAgent     {@code User-Agent}
 */
public record ClientInfo(String correlationId, String requestId, String ipAddress, String userAgent) {
}
