package com.railcab.nms.infrastructure.cache;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.github.benmanes.caffeine.cache.Ticker;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;

/**
 * A bounded, single-flight Caffeine cache of lookups that may find nothing. A found value lives for {@code ttl}, an empty
 * answer for the (shorter) {@code notFoundTtl}. A loader that throws leaves nothing behind, so failures are never cached
 * and the exception reaches the caller. Statistics are published to Micrometer under the cache name when a registry exists.
 *
 * @param <K> key type
 * @param <V> value type
 */
public final class OptionalTtlCache<K, V> {

    /** Meter name of the load-time timer (tagged {@code cache=<name>}). */

    private final Cache<K, Optional<V>> cache;
    private final Timer loadTimer;

    /**
     * @param name        metric name (the {@code cache} tag)
     * @param ttl         lifetime of a found value
     * @param notFoundTtl lifetime of an empty answer
     * @param maxSize     most entries kept; the least useful are evicted beyond it
     * @param ticker      time source (a fake one in tests)
     * @param registry    where to publish metrics, or {@code null} for none
     */
    public OptionalTtlCache(String name, Duration ttl, Duration notFoundTtl, long maxSize, Ticker ticker,
                            MeterRegistry registry) {
        long foundNanos = ttl.toNanos();
        long emptyNanos = notFoundTtl.toNanos();
        this.cache = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .ticker(ticker)
                .expireAfter(new Expiry<K, Optional<V>>() {
                    @Override
                    public long expireAfterCreate(K key, Optional<V> value, long currentTime) {
                        return value.isPresent() ? foundNanos : emptyNanos;
                    }

                    @Override
                    public long expireAfterUpdate(K key, Optional<V> value, long currentTime, long currentDuration) {
                        return expireAfterCreate(key, value, currentTime);
                    }

                    @Override
                    public long expireAfterRead(K key, Optional<V> value, long currentTime, long currentDuration) {
                        return currentDuration;
                    }
                })
                .recordStats()
                .build();
        if (registry != null) {
            CaffeineCacheMetrics.monitor(registry, cache, name);
            this.loadTimer = Timer.builder(NmsCacheConstants.LOAD_TIMER_NAME).description("Time spent loading a missing cache entry")
                    .tag("cache", name).register(registry);
        } else {
            this.loadTimer = null;
        }
    }

    /** The cached answer for {@code key}, loading it once (even under concurrent misses) when absent or expired. */
    public Optional<V> get(K key, Supplier<Optional<V>> loader) {
        if (loadTimer == null) {
            return cache.get(key, k -> loader.get());
        }
        // Timed here because Micrometer's Caffeine binder only exposes load time for LoadingCache; failed loads are timed too.
        return cache.get(key, k -> loadTimer.record(loader));
    }

    /** Forgets every entry. */
    public void invalidateAll() {
        cache.invalidateAll();
    }

    /** Entries currently held (approximate, includes entries not yet cleaned up after expiry). */
    public long estimatedSize() {
        return cache.estimatedSize();
    }

    /** Runs pending maintenance (expired-entry removal); mostly for tests. */
    public void cleanUp() {
        cache.cleanUp();
    }
}
