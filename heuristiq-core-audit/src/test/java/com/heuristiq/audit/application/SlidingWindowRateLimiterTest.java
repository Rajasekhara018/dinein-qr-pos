package com.heuristiq.audit.application;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class SlidingWindowRateLimiterTest {

    /** Clock whose time the test moves. */
    private static final class MovingClock extends Clock {
        final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-06T10:00:00Z"));

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    }

    private final MovingClock clock = new MovingClock();
    private final SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(clock);

    @Test
    void grantsUpToTheLimitThenNothing() {
        assertThat(limiter.acquire("u", 40, 60)).isEqualTo(40);
        assertThat(limiter.acquire("u", 40, 60)).isEqualTo(20);
        assertThat(limiter.acquire("u", 1, 60)).isZero();
    }

    @Test
    void keysAreIndependent() {
        assertThat(limiter.acquire("a", 60, 60)).isEqualTo(60);
        assertThat(limiter.acquire("b", 60, 60)).isEqualTo(60);
    }

    @Test
    void budgetRefillsAfterTheWindow() {
        assertThat(limiter.acquire("u", 60, 60)).isEqualTo(60);
        clock.now.set(clock.now.get().plusSeconds(59));
        assertThat(limiter.acquire("u", 1, 60)).isZero();
        clock.now.set(clock.now.get().plusSeconds(2));
        assertThat(limiter.acquire("u", 10, 60)).isEqualTo(10);
    }
}
