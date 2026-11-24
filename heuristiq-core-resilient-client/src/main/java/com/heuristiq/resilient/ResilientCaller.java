package com.heuristiq.resilient;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.reactor.bulkhead.operator.BulkheadOperator;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.retry.RetryOperator;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.io.IOException;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

/**
 * Wraps one downstream's calls: timeout, then bulkhead, then circuit breaker, then retry (outermost,
 * so every attempt is counted by the breaker and an open breaker stops further attempts).
 *
 * <ul>
 *   <li>Retries only {@link Idempotency#SAFE} calls, only on transient failures (timeout, connection
 *       error, 429/502/503/504), with exponential backoff and jitter.</li>
 *   <li>4xx responses are real answers: never retried, never counted against the breaker, and rethrown
 *       unchanged so callers keep their existing not-found / validation handling.</li>
 *   <li>Everything else surfaces as {@link DownstreamUnavailableException}.</li>
 * </ul>
 * One instance per downstream; it is thread-safe.
 */
public final class ResilientCaller {

    private final String name;
    private final ResilienceProfile profile;
    private final CircuitBreaker breaker;
    private final Bulkhead bulkhead;
    private final Retry safeRetry;

    public ResilientCaller(String name, ResilienceProfile profile) {
        this.name = name;
        this.profile = profile;
        this.breaker = CircuitBreaker.of(name, CircuitBreakerConfig.custom()
                .slidingWindowSize(profile.breakerWindowSize())
                .minimumNumberOfCalls(profile.breakerMinimumCalls())
                .failureRateThreshold(profile.breakerFailureRatePercent())
                .waitDurationInOpenState(profile.breakerOpenDuration())
                .permittedNumberOfCallsInHalfOpenState(profile.breakerHalfOpenCalls())
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .recordException(ResilientCaller::countsAgainstBreaker)
                .build());
        this.bulkhead = Bulkhead.of(name, BulkheadConfig.custom()
                .maxConcurrentCalls(profile.maxConcurrentCalls())
                .maxWaitDuration(profile.bulkheadMaxWait())
                .build());
        this.safeRetry = Retry.of(name, RetryConfig.custom()
                .maxAttempts(Math.max(ResilienceConstants.MIN_ATTEMPTS, profile.maxAttempts()))
                .intervalFunction(IntervalFunction.ofExponentialRandomBackoff(
                        profile.retryInitialBackoff(), profile.retryMultiplier(),
                        profile.retryJitter(), profile.retryMaxBackoff()))
                .retryOnException(ResilientCaller::isRetryable)
                .build());
    }

    public String name() { return name; }

    public CircuitBreaker circuitBreaker() { return breaker; }

    /** Runs {@code call} under the profile. The supplier is invoked once per attempt. */
    public <T> Mono<T> call(Supplier<Mono<T>> call, Idempotency idempotency) {
        Mono<T> guarded = Mono.defer(call)
                .timeout(profile.callTimeout())
                .transformDeferred(BulkheadOperator.of(bulkhead))
                .transformDeferred(CircuitBreakerOperator.of(breaker));
        if (idempotency == Idempotency.SAFE) {
            guarded = guarded.transformDeferred(RetryOperator.of(safeRetry));
        }
        return guarded.onErrorMap(this::translate);
    }

    /** Blocking convenience for callers not yet on a reactive chain. Still bounded by the timeouts. */
    public <T> T callBlocking(Supplier<Mono<T>> call, Idempotency idempotency) {
        return call(call, idempotency).block();
    }

    private Throwable translate(Throwable t) {
        if (t instanceof DownstreamUnavailableException) return t;
        if (t instanceof CallNotPermittedException) {
            return new DownstreamUnavailableException(name, DownstreamUnavailableException.Reason.CIRCUIT_OPEN, t);
        }
        if (t instanceof BulkheadFullException) {
            return new DownstreamUnavailableException(name, DownstreamUnavailableException.Reason.OVERLOADED, t);
        }
        if (isTimeout(t)) {
            return new DownstreamUnavailableException(name, DownstreamUnavailableException.Reason.TIMEOUT, t);
        }
        if (countsAgainstBreaker(t)) {
            return new DownstreamUnavailableException(name, DownstreamUnavailableException.Reason.FAILED, t);
        }
        return t;
    }

    static boolean isTimeout(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause() == c ? null : c.getCause()) {
            if (c instanceof TimeoutException || c.getClass().getName().endsWith(ResilienceConstants.TIMEOUT_EXCEPTION_SUFFIX)) return true;
        }
        return false;
    }

    /** Safe to repeat: timeouts, connection failures, and 429/502/503/504. A plain 500 is not repeated. */
    static boolean isRetryable(Throwable t) {
        if (isTimeout(t)) return true;
        if (t instanceof WebClientResponseException r) {
            int s = r.getStatusCode().value();
            return s == ResilienceConstants.STATUS_TOO_MANY_REQUESTS || s == ResilienceConstants.STATUS_BAD_GATEWAY
                    || s == ResilienceConstants.STATUS_SERVICE_UNAVAILABLE
                    || s == ResilienceConstants.STATUS_GATEWAY_TIMEOUT;
        }
        return t instanceof WebClientRequestException || t instanceof IOException;
    }

    /** Server-side trouble trips the breaker; a 4xx (the caller's mistake) never does. */
    static boolean countsAgainstBreaker(Throwable t) {
        if (t instanceof WebClientResponseException r) {
            return r.getStatusCode().is5xxServerError() || r.getStatusCode().value() == ResilienceConstants.STATUS_TOO_MANY_REQUESTS;
        }
        return isRetryable(t);
    }
}
