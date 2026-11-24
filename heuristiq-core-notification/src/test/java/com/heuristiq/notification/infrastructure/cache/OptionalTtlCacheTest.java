package com.heuristiq.notification.infrastructure.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class OptionalTtlCacheTest {

    private FakeTicker ticker;
    private MeterRegistry registry;
    private OptionalTtlCache<String, String> cache;
    private AtomicInteger loads;

    @BeforeEach
    void setUp() {
        ticker = new FakeTicker();
        registry = new SimpleMeterRegistry();
        cache = new OptionalTtlCache<>("test.cache", Duration.ofSeconds(60), Duration.ofSeconds(10), 3, ticker, registry);
        loads = new AtomicInteger();
    }

    private Optional<String> load(String value) {
        loads.incrementAndGet();
        return Optional.ofNullable(value);
    }

    @Test
    void secondLookupIsAHitAndDoesNotLoadAgain() {
        assertThat(cache.get("a", () -> load("A"))).contains("A");
        assertThat(cache.get("a", () -> load("OTHER"))).contains("A");
        assertThat(loads).hasValue(1);
    }

    @Test
    void foundValueExpiresAfterTtl() {
        cache.get("a", () -> load("A"));
        ticker.advance(Duration.ofSeconds(59));
        assertThat(cache.get("a", () -> load("B"))).contains("A");
        ticker.advance(Duration.ofSeconds(2));
        assertThat(cache.get("a", () -> load("B"))).contains("B");
        assertThat(loads).hasValue(2);
    }

    @Test
    void emptyAnswerIsKeptOnlyForTheShorterNotFoundTtl() {
        assertThat(cache.get("a", () -> load(null))).isEmpty();
        ticker.advance(Duration.ofSeconds(9));
        assertThat(cache.get("a", () -> load("late"))).isEmpty();
        assertThat(loads).hasValue(1);
        ticker.advance(Duration.ofSeconds(2));
        assertThat(cache.get("a", () -> load("late"))).contains("late");
        assertThat(loads).hasValue(2);
    }

    @Test
    void aFailingLoaderIsNotCachedAndTheExceptionPropagates() {
        assertThatThrownBy(() -> cache.get("a", () -> {
            loads.incrementAndGet();
            throw new IllegalStateException("downstream down");
        })).isInstanceOf(IllegalStateException.class).hasMessage("downstream down");
        assertThat(cache.get("a", () -> load("ok"))).contains("ok");
        assertThat(loads).hasValue(2);
    }

    @Test
    void invalidateAllForgetsEverything() {
        cache.get("a", () -> load("A"));
        cache.invalidateAll();
        assertThat(cache.get("a", () -> load("A2"))).contains("A2");
    }

    @Test
    void sizeIsBounded() {
        for (int i = 0; i < 20; i++) {
            String key = "k" + i;
            cache.get(key, () -> load(key));
        }
        cache.cleanUp();
        assertThat(cache.estimatedSize()).isLessThanOrEqualTo(3);
    }

    @Test
    void concurrentMissesLoadOnlyOnce() throws Exception {
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch started = new CountDownLatch(threads);
        CountDownLatch release = new CountDownLatch(1);
        try {
            List<Future<Optional<String>>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                results.add(pool.submit(() -> {
                    started.countDown();
                    return cache.get("hot", () -> {
                        loads.incrementAndGet();
                        try {
                            release.await();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        return Optional.of("V");
                    });
                }));
            }
            started.await();
            release.countDown();
            for (Future<Optional<String>> f : results) {
                assertThat(f.get()).contains("V");
            }
            assertThat(loads).hasValue(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void publishesHitMissSizeEvictionAndLoadMetricsUnderTheCacheName() {
        cache.get("a", () -> load("A"));
        cache.get("a", () -> load("A"));
        for (int i = 0; i < 10; i++) {
            String key = "e" + i;
            cache.get(key, () -> load(key));
        }
        cache.cleanUp();

        assertThat(registry.find("cache.gets").tags("cache", "test.cache", "result", "hit").functionCounter().count())
                .isEqualTo(1.0);
        assertThat(registry.find("cache.gets").tags("cache", "test.cache", "result", "miss").functionCounter().count())
                .isEqualTo(11.0);
        assertThat(registry.find("cache.size").tag("cache", "test.cache").gauge()).isNotNull();
        assertThat(registry.find("cache.evictions").tag("cache", "test.cache").functionCounter().count()).isGreaterThan(0.0);
        assertThat(registry.find("cache.load.duration").tag("cache", "test.cache").timer().count()).isEqualTo(11L);
    }
}
