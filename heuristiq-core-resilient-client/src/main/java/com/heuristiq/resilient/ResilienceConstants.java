package com.heuristiq.resilient;

import java.time.Duration;

/**
 * Fixed values used inside the library: the default {@link ResilienceProfile} limits and the HTTP
 * statuses treated as transient. Package-private on purpose so it never becomes part of the public API;
 * the public header and MDC names stay on {@link CorrelationIdPropagation} and {@link TenantIdPropagation}.
 */
final class ResilienceConstants {

    // Default profile limits (see ResilienceProfile#defaults()).
    static final Duration DEFAULT_CALL_TIMEOUT = Duration.ofSeconds(5);
    static final int DEFAULT_MAX_ATTEMPTS = 3;
    static final Duration DEFAULT_RETRY_INITIAL_BACKOFF = Duration.ofMillis(200);
    static final double DEFAULT_RETRY_MULTIPLIER = 2.0;
    static final double DEFAULT_RETRY_JITTER = 0.5;
    static final Duration DEFAULT_RETRY_MAX_BACKOFF = Duration.ofSeconds(2);
    static final int DEFAULT_MAX_CONCURRENT_CALLS = 25;
    static final Duration DEFAULT_BULKHEAD_MAX_WAIT = Duration.ZERO;
    static final int DEFAULT_BREAKER_WINDOW_SIZE = 20;
    static final int DEFAULT_BREAKER_MINIMUM_CALLS = 10;
    static final float DEFAULT_BREAKER_FAILURE_RATE_PERCENT = 50f;
    static final Duration DEFAULT_BREAKER_OPEN_DURATION = Duration.ofSeconds(15);
    static final int DEFAULT_BREAKER_HALF_OPEN_CALLS = 5;

    /** Minimum number of attempts a retry is configured with (one attempt means no retry). */
    static final int MIN_ATTEMPTS = 1;

    // HTTP statuses that mean "try again later".
    static final int STATUS_TOO_MANY_REQUESTS = 429;
    static final int STATUS_BAD_GATEWAY = 502;
    static final int STATUS_SERVICE_UNAVAILABLE = 503;
    static final int STATUS_GATEWAY_TIMEOUT = 504;

    /** Simple-name suffix of exception classes treated as timeouts (Netty, Reactor, JDK variants). */
    static final String TIMEOUT_EXCEPTION_SUFFIX = "TimeoutException";

    private ResilienceConstants() {
    }
}
