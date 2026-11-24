package com.heuristiq.gateway.config;

import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

/**
 * How the gateway retries a read that reached an instance which has just stopped or is not ready yet (a deploy, a pod being
 * replaced): the second try goes to another instance. Only GET is retried, so nothing is ever done twice.
 *
 * <p>What is retried is deliberately narrow: only the statuses in {@link #RETRY_STATUSES} (the retry filter's default
 * "every 5xx" series is cleared, so a 500 from the service is not repeated) and only I/O failures such as a refused
 * or reset connection ({@link #RETRY_EXCEPTIONS}); a call that timed out is not repeated. The retry runs inside the
 * route's circuit breaker, so the breaker's own 503 fallback answer is never retried.
 */
public final class RouteRetryConstants {

    /** Extra tries after the first; kept low so a real outage still fails fast. */
    public static final int RETRIES = 2;

    public static final Duration FIRST_BACKOFF = Duration.ofMillis(100);
    public static final Duration MAX_BACKOFF = Duration.ofMillis(800);
    public static final int BACKOFF_FACTOR = 2;

    /** Answers from the service that mean "this instance cannot serve now", worth one more try elsewhere. */
    public static final List<HttpStatus> RETRY_STATUSES = List.of(
            HttpStatus.BAD_GATEWAY, HttpStatus.SERVICE_UNAVAILABLE, HttpStatus.GATEWAY_TIMEOUT);

    /** Failures before any answer: connection refused or reset, connect timeout. Not the response timeout. */
    public static final List<Class<? extends Throwable>> RETRY_EXCEPTIONS = List.of(IOException.class);

    private RouteRetryConstants() {
    }
}
