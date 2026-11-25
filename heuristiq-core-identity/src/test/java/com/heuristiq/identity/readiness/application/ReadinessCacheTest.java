package com.heuristiq.identity.readiness.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import com.heuristiq.identity.readiness.domain.ApplicationReadiness;
import com.heuristiq.identity.readiness.domain.OverallReadinessStatus;

/** The cache: per organization and application, any code case, expiring after its lifetime. */
class ReadinessCacheTest {

    private static ApplicationReadiness result(String code) {
        return new ApplicationReadiness(code, code, Instant.EPOCH, OverallReadinessStatus.READY, 0, 0, 0, null, List.of());
    }

    @Test
    void keyedByOrganizationAndApplication() {
        AtomicLong ticks = new AtomicLong();
        ReadinessCache cache = new ReadinessCache(Duration.ofSeconds(30), 10, ticks::get);
        ApplicationReadiness forA = result("BEST_PURCHASE_APP");
        cache.put("org-a", "best_purchase_app", forA);

        assertThat(cache.get("org-a", "BEST_PURCHASE_APP")).containsSame(forA);
        assertThat(cache.get("org-b", "BEST_PURCHASE_APP")).isEmpty();
        assertThat(cache.get("org-a", "FOOD_DELIVER_APP")).isEmpty();

        ticks.addAndGet(Duration.ofSeconds(29).toNanos());
        assertThat(cache.get("org-a", "BEST_PURCHASE_APP")).isPresent();
        ticks.addAndGet(Duration.ofSeconds(2).toNanos());
        assertThat(cache.get("org-a", "BEST_PURCHASE_APP")).isEmpty();
    }

    @Test
    void evictingForgetsOnlyThatOrganizationsApplication() {
        ReadinessCache cache = new ReadinessCache(Duration.ofSeconds(30), 10, new AtomicLong()::get);
        cache.put("org-a", "BEST_PURCHASE_APP", result("BEST_PURCHASE_APP"));
        cache.put("org-a", "FOOD_DELIVER_APP", result("FOOD_DELIVER_APP"));
        cache.put("org-b", "BEST_PURCHASE_APP", result("BEST_PURCHASE_APP"));

        cache.evict("org-a", "best_purchase_app");

        assertThat(cache.get("org-a", "BEST_PURCHASE_APP")).isEmpty();
        assertThat(cache.get("org-a", "FOOD_DELIVER_APP")).isPresent();
        assertThat(cache.get("org-b", "BEST_PURCHASE_APP")).isPresent();
    }
}
