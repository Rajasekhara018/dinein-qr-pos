package com.heuristiq.uam.cache;

import com.github.benmanes.caffeine.cache.Ticker;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UamCacheTest {

    /** A ticker the test advances by hand, so expiry never needs a sleep. */
    static final class FakeTicker implements Ticker {
        private final AtomicLong nanos = new AtomicLong();

        @Override
        public long read() {
            return nanos.get();
        }

        void advanceSeconds(long seconds) {
            nanos.addAndGet(TimeUnit.SECONDS.toNanos(seconds));
        }
    }

    private final FakeTicker ticker = new FakeTicker();

    private UamCache<String, String> cache(boolean enabled, MeterRegistry registry) {
        return new UamCache<>("uam.test", enabled, 30, 100, ticker, registry);
    }

    @Test
    void secondReadIsAHitAndTheLoaderRunsOnce() {
        UamCache<String, String> cache = cache(true, null);
        AtomicInteger loads = new AtomicInteger();

        assertThat(cache.get("k", () -> "v" + loads.incrementAndGet())).isEqualTo("v1");
        assertThat(cache.get("k", () -> "v" + loads.incrementAndGet())).isEqualTo("v1");

        assertThat(loads).hasValue(1);
    }

    @Test
    void entryExpiresAfterTheTtlOnTheFakeTicker() {
        UamCache<String, String> cache = cache(true, null);
        AtomicInteger loads = new AtomicInteger();

        cache.get("k", () -> "v" + loads.incrementAndGet());
        ticker.advanceSeconds(29);
        cache.get("k", () -> "v" + loads.incrementAndGet());
        assertThat(loads).hasValue(1);

        ticker.advanceSeconds(2);
        assertThat(cache.get("k", () -> "v" + loads.incrementAndGet())).isEqualTo("v2");
    }

    @Test
    void failedAndNullLoadsAreNotCached() {
        UamCache<String, String> cache = cache(true, null);
        AtomicInteger loads = new AtomicInteger();

        assertThatThrownBy(() -> cache.get("k", () -> {
            loads.incrementAndGet();
            throw new IllegalStateException("down");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(cache.get("k", () -> {
            loads.incrementAndGet();
            return null;
        })).isNull();
        assertThat(cache.get("k", () -> {
            loads.incrementAndGet();
            return "ok";
        })).isEqualTo("ok");

        assertThat(loads).hasValue(3);
    }

    @Test
    void disabledCacheIsAPassThrough() {
        UamCache<String, String> cache = cache(false, null);
        AtomicInteger loads = new AtomicInteger();

        cache.get("k", () -> "v" + loads.incrementAndGet());
        cache.get("k", () -> "v" + loads.incrementAndGet());

        assertThat(loads).hasValue(2);
        assertThat(cache.estimatedSize()).isZero();
    }

    @Test
    void invalidateIfRemovesOnlyMatchingKeys() {
        UamCache<String, String> cache = cache(true, null);
        cache.get("a:1", () -> "x");
        cache.get("b:1", () -> "y");

        cache.invalidateIf(k -> k.startsWith("a:"));

        assertThat(cache.estimatedSize()).isEqualTo(1);
    }

    @Test
    void concurrentMissesForOneKeyRunOneLoad() throws Exception {
        UamCache<String, String> cache = cache(true, null);
        AtomicInteger loads = new AtomicInteger();
        CountDownLatch release = new CountDownLatch(1);
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<String>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                results.add(pool.submit(() -> cache.get("k", () -> {
                    loads.incrementAndGet();
                    try {
                        release.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return "v";
                })));
            }
            // Wait until the single loader is running (no sleep: spin on the counter with a bounded deadline).
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (loads.get() == 0 && System.nanoTime() < deadline) {
                Thread.onSpinWait();
            }
            release.countDown();
            for (Future<String> result : results) {
                assertThat(result.get(10, TimeUnit.SECONDS)).isEqualTo("v");
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(loads).hasValue(1);
    }

    @Test
    void publishesHitMissSizeAndEvictionMeters() {
        MeterRegistry registry = new SimpleMeterRegistry();
        UamCaches caches = UamCaches.create(new UamCacheProperties(), ticker, registry);
        caches.myPrivileges().get(UamCacheKeys.TenantUserKey.of("t", "u"), () -> null);

        for (String name : List.of(UamCacheConstants.MY_PRIVILEGES, UamCacheConstants.MY_ORGANIZATIONS,
                UamCacheConstants.TENANTS, UamCacheConstants.TENANT_APPLICATIONS)) {
            assertThat(registry.find("cache.gets").tag("cache", name).tag("result", "hit").functionCounter()).as(name + " hits").isNotNull();
            assertThat(registry.find("cache.gets").tag("cache", name).tag("result", "miss").functionCounter()).as(name + " misses").isNotNull();
            assertThat(registry.find("cache.size").tag("cache", name).gauge()).as(name + " size").isNotNull();
            assertThat(registry.find("cache.evictions").tag("cache", name).functionCounter()).as(name + " evictions").isNotNull();
            assertThat(registry.find("cache.load.duration").tag("cache", name).meter()).as(name + " load time").isNotNull();
        }
    }

    @Test
    void propertiesRejectOutOfRangeValuesAndDefaultToEnabled() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        UamCacheProperties good = new UamCacheProperties();
        assertThat(good.isEnabled()).isTrue();
        assertThat(good.getMyPrivileges().getTtlSeconds()).isEqualTo(30);
        assertThat(good.getTenants().getTtlSeconds()).isEqualTo(120);
        assertThat(validator.validate(good)).isEmpty();

        UamCacheProperties bad = new UamCacheProperties();
        bad.getMyPrivileges().setTtlSeconds(0);
        bad.getTenants().setMaxSize(UamCacheConstants.MAX_SIZE_LIMIT + 1);
        bad.getMyOrganizations().setTtlSeconds(UamCacheConstants.MAX_TTL_SECONDS + 1);
        assertThat(validator.validate(bad)).hasSize(3);
    }
}
