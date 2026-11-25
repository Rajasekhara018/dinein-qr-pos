package com.heuristiq.identity.readiness.application;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;

import com.heuristiq.identity.readiness.domain.ApplicationReadiness;

/**
 * Short, bounded cache of readiness results. The key is (organization id, application code), so one organization's result
 * is never served to another.
 */
public class ReadinessCache {

    private final Cache<Key, ApplicationReadiness> cache;

    /**
     * @param ttl     how long a result is reused
     * @param maxSize most results kept
     * @param ticker  time source (tests pass a fake one)
     */
    public ReadinessCache(Duration ttl, long maxSize, Ticker ticker) {
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(ttl)
                .maximumSize(maxSize)
                .ticker(ticker)
                .build();
    }

    /** The cached result for the organization and application, if still fresh. */
    public Optional<ApplicationReadiness> get(String tenantId, String applicationCode) {
        return Optional.ofNullable(cache.getIfPresent(key(tenantId, applicationCode)));
    }

    /** Stores (or replaces) the result for the organization and application. */
    public void put(String tenantId, String applicationCode, ApplicationReadiness readiness) {
        cache.put(key(tenantId, applicationCode), readiness);
    }

    /** Removes the cached result of the organization and application (after its severity overrides changed). */
    public void evict(String tenantId, String applicationCode) {
        cache.invalidate(key(tenantId, applicationCode));
    }

    private static Key key(String tenantId, String applicationCode) {
        return new Key(tenantId, applicationCode.toUpperCase(Locale.ROOT));
    }

    private record Key(String tenantId, String applicationCode) {
    }
}
