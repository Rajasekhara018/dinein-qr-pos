package com.heuristiq.identity.readiness.application;

import java.time.Duration;

/**
 * Limits of the readiness combiner, handed in by the configuration layer.
 *
 * @param budget       overall time to wait for the services of one request; a service still running then is UNKNOWN
 * @param cacheTtl     how long a result is reused for the same organization and application
 * @param cacheMaxSize most results kept in memory
 */
public record ReadinessSettings(Duration budget, Duration cacheTtl, long cacheMaxSize) {
}
