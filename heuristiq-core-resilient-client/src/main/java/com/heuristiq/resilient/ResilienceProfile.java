package com.heuristiq.resilient;

import java.time.Duration;

/**
 * Per-downstream limits. Build one per downstream from properties or start from {@link #defaults()}.
 * Retry totals must stay below the caller's own deadline: with the defaults the worst case is
 * 3 x 5 s plus backoff, so callers inside a user request should lower attempts or the timeout.
 */
public record ResilienceProfile(
        Duration callTimeout,
        int maxAttempts,
        Duration retryInitialBackoff,
        double retryMultiplier,
        double retryJitter,
        Duration retryMaxBackoff,
        int maxConcurrentCalls,
        Duration bulkheadMaxWait,
        int breakerWindowSize,
        int breakerMinimumCalls,
        float breakerFailureRatePercent,
        Duration breakerOpenDuration,
        int breakerHalfOpenCalls) {

    public static ResilienceProfile defaults() {
        return new ResilienceProfile(
                ResilienceConstants.DEFAULT_CALL_TIMEOUT, ResilienceConstants.DEFAULT_MAX_ATTEMPTS,
                ResilienceConstants.DEFAULT_RETRY_INITIAL_BACKOFF, ResilienceConstants.DEFAULT_RETRY_MULTIPLIER,
                ResilienceConstants.DEFAULT_RETRY_JITTER, ResilienceConstants.DEFAULT_RETRY_MAX_BACKOFF,
                ResilienceConstants.DEFAULT_MAX_CONCURRENT_CALLS, ResilienceConstants.DEFAULT_BULKHEAD_MAX_WAIT,
                ResilienceConstants.DEFAULT_BREAKER_WINDOW_SIZE, ResilienceConstants.DEFAULT_BREAKER_MINIMUM_CALLS,
                ResilienceConstants.DEFAULT_BREAKER_FAILURE_RATE_PERCENT,
                ResilienceConstants.DEFAULT_BREAKER_OPEN_DURATION,
                ResilienceConstants.DEFAULT_BREAKER_HALF_OPEN_CALLS);
    }

    public ResilienceProfile withCallTimeout(Duration d) {
        return new ResilienceProfile(d, maxAttempts, retryInitialBackoff, retryMultiplier, retryJitter,
                retryMaxBackoff, maxConcurrentCalls, bulkheadMaxWait, breakerWindowSize, breakerMinimumCalls,
                breakerFailureRatePercent, breakerOpenDuration, breakerHalfOpenCalls);
    }

    public ResilienceProfile withMaxAttempts(int n) {
        return new ResilienceProfile(callTimeout, n, retryInitialBackoff, retryMultiplier, retryJitter,
                retryMaxBackoff, maxConcurrentCalls, bulkheadMaxWait, breakerWindowSize, breakerMinimumCalls,
                breakerFailureRatePercent, breakerOpenDuration, breakerHalfOpenCalls);
    }

    public ResilienceProfile withMaxConcurrentCalls(int n) {
        return new ResilienceProfile(callTimeout, maxAttempts, retryInitialBackoff, retryMultiplier, retryJitter,
                retryMaxBackoff, n, bulkheadMaxWait, breakerWindowSize, breakerMinimumCalls,
                breakerFailureRatePercent, breakerOpenDuration, breakerHalfOpenCalls);
    }

    public ResilienceProfile withBreaker(int window, int minCalls, float failurePercent, Duration open) {
        return new ResilienceProfile(callTimeout, maxAttempts, retryInitialBackoff, retryMultiplier, retryJitter,
                retryMaxBackoff, maxConcurrentCalls, bulkheadMaxWait, window, minCalls, failurePercent, open,
                breakerHalfOpenCalls);
    }
}
