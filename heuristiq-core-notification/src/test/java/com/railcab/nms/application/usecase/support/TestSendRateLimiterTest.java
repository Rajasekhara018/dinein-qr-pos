package com.railcab.nms.application.usecase.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class TestSendRateLimiterTest {

    /** A clock the test can move forward. */
    private static final class MovableClock extends Clock {
        private Instant now = Instant.parse("2026-10-18T10:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    @Test
    void allowsUpToTheLimitThenRefusesWithoutRecordingTheRefusal() {
        MovableClock clock = new MovableClock();
        TestSendRateLimiter limiter = new TestSendRateLimiter(clock, 2);

        assertThat(limiter.tryAcquire("a")).isTrue();
        assertThat(limiter.tryAcquire("a")).isTrue();
        assertThat(limiter.tryAcquire("a")).isFalse();
        assertThat(limiter.tryAcquire("b")).isTrue();
    }

    @Test
    void sendsAgeOutOfTheWindow() {
        MovableClock clock = new MovableClock();
        TestSendRateLimiter limiter = new TestSendRateLimiter(clock, 1);

        assertThat(limiter.tryAcquire("a")).isTrue();
        assertThat(limiter.tryAcquire("a")).isFalse();

        clock.advance(TemplateTestSendConstants.RATE_WINDOW.plusSeconds(1));

        assertThat(limiter.tryAcquire("a")).isTrue();
    }
}
