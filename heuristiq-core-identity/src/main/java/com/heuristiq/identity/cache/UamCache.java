package com.heuristiq.uam.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.TimeGauge;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * A bounded, time-limited, single-flight read cache. Concurrent misses for one key run one load. When disabled every call
 * runs the loader (pass-through). A loader that throws, or returns null, stores nothing, so failures and "not found"
 * are never cached. Values must be immutable (or copied by the caller); entries are visible only on this instance, so
 * other UAM instances converge within the TTL.
 *
 * @param <K> key type (a record carrying the organization and the entity id)
 * @param <V> immutable value type
 */
public final class UamCache<K, V> {

    private final String name;
    private final boolean enabled;
    private final Cache<K, V> cache;

    /**
     * @param registry optional; when set the cache publishes hit/miss/size/eviction/load-time meters tagged with its name
     */
    public UamCache(String name, boolean enabled, int ttlSeconds, int maxSize, Ticker ticker, MeterRegistry registry) {
        this.name = name;
        this.enabled = enabled;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(ttlSeconds))
                .maximumSize(maxSize)
                .ticker(ticker)
                .recordStats()
                .build();
        if (registry != null) {
            CaffeineCacheMetrics.monitor(registry, cache, name);
            // CaffeineCacheMetrics reports load time only for LoadingCache; this cache loads through get(key, fn).
            TimeGauge.builder("cache.load.duration", cache, TimeUnit.NANOSECONDS, c -> c.stats().totalLoadTime())
                    .tag("cache", name).description("Total time spent loading entries").register(registry);
        }
    }

    public String name() {
        return name;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** The cached value, or the loader's result (stored unless null). Disabled: always the loader's result. */
    public V get(K key, Supplier<V> loader) {
        if (!enabled) {
            return loader.get();
        }
        return cache.get(key, k -> loader.get());
    }

    /** Removes every entry whose key matches. */
    public void invalidateIf(Predicate<K> matcher) {
        cache.asMap().keySet().removeIf(matcher);
    }

    public void invalidate(K key) {
        cache.invalidate(key);
    }

    public void invalidateAll() {
        cache.invalidateAll();
    }

    public long estimatedSize() {
        cache.cleanUp();
        return cache.estimatedSize();
    }
}
