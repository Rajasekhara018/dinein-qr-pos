package com.heuristiq.configclient;

import java.time.Duration;

/**
 * Tuning for {@link ConfigClient}.
 *
 * @param serviceToken     shared service token sent to Config; required
 * @param pollInterval     how often {@link ConfigClient#start()} polls the changes feed
 * @param maxCacheEntries  hard bound on cached entries (least recently used is evicted)
 * @param staleTtl         how long cached entries are trusted without any successful contact with
 *                         Config; past it a read tries to re-resolve, but the stale value is still
 *                         served if Config is unreachable
 */
public record ConfigClientOptions(String serviceToken, Duration pollInterval, int maxCacheEntries,
                                  Duration staleTtl) {

    public ConfigClientOptions {
        if (serviceToken == null || serviceToken.isBlank()) {
            throw new IllegalArgumentException("serviceToken must not be blank");
        }
        if (pollInterval == null || pollInterval.isZero() || pollInterval.isNegative()) {
            throw new IllegalArgumentException("pollInterval must be positive");
        }
        if (maxCacheEntries < 1) {
            throw new IllegalArgumentException("maxCacheEntries must be at least 1");
        }
        if (staleTtl == null || staleTtl.isZero() || staleTtl.isNegative()) {
            throw new IllegalArgumentException("staleTtl must be positive");
        }
    }

    /** Options with the default poll interval, cache bound and stale TTL. */
    public static ConfigClientOptions of(String serviceToken) {
        return new ConfigClientOptions(serviceToken, ConfigClientConstants.DEFAULT_POLL_INTERVAL,
                ConfigClientConstants.DEFAULT_MAX_CACHE_ENTRIES, ConfigClientConstants.DEFAULT_STALE_TTL);
    }

    /** Never prints the token. */
    @Override
    public String toString() {
        return "ConfigClientOptions[pollInterval=" + pollInterval + ", maxCacheEntries=" + maxCacheEntries
                + ", staleTtl=" + staleTtl + "]";
    }
}
