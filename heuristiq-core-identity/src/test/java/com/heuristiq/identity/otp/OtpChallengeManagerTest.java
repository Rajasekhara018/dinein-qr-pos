package com.heuristiq.identity.otp;

import com.heuristiq.identity.common.UamException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OtpChallengeManagerTest {
    private static final String KEY = "CUSTOMER:PHONE:9876543210";
    private static final String OTP = "123456";

    private InMemoryOtpStore store;
    private MutableClock clock;
    private OtpChallengeManager manager;

    @BeforeEach
    void setUp() {
        store = new InMemoryOtpStore();
        clock = new MutableClock(Instant.parse("2026-01-01T10:00:00Z"));
        manager = new OtpChallengeManager(store, clock, Duration.ofMinutes(5), 5, Duration.ofSeconds(30), 5);
    }

    @Test
    void correctOtpVerifiesOnce() {
        manager.issue(KEY, OTP);
        assertThat(manager.verify(KEY, OTP, true)).isTrue();
        assertThat(manager.verify(KEY, OTP, true)).as("single use").isFalse();
    }

    @Test
    void otpIsNotStoredInPlaintextAndSaltIsPerChallenge() {
        manager.issue(KEY, OTP);
        OtpChallenge first = store.raw(KEY);
        assertThat(first.getOtpHash()).isNotBlank().doesNotContain(OTP).isNotEqualTo(OTP);
        String firstSalt = first.getOtpSalt();
        String firstHash = first.getOtpHash();

        clock.advance(Duration.ofSeconds(31));
        manager.issue(KEY, OTP);
        assertThat(store.raw(KEY).getOtpSalt()).isNotEqualTo(firstSalt);
        assertThat(store.raw(KEY).getOtpHash()).as("same OTP, new salt, new hash").isNotEqualTo(firstHash);
    }

    @Test
    void wrongAttemptsInvalidateTheChallengeEvenForTheCorrectOtp() {
        manager.issue(KEY, OTP);
        for (int i = 0; i < 4; i++) {
            assertThat(manager.verify(KEY, "000000", true)).isFalse();
        }
        assertThat(store.raw(KEY).getFailedAttempts()).isEqualTo(4);
        assertThat(manager.verify(KEY, "000000", true)).isFalse();
        assertThat(manager.verify(KEY, OTP, true)).as("locked out after the 5th wrong guess").isFalse();
    }

    @Test
    void lastAllowedAttemptStillAcceptsTheCorrectOtp() {
        manager.issue(KEY, OTP);
        for (int i = 0; i < 4; i++) {
            manager.verify(KEY, "000000", true);
        }
        assertThat(manager.verify(KEY, OTP, true)).isTrue();
    }

    @Test
    void newOtpAfterLockoutWorksAndResetsAttempts() {
        manager.issue(KEY, OTP);
        for (int i = 0; i < 5; i++) {
            manager.verify(KEY, "000000", true);
        }
        clock.advance(Duration.ofSeconds(31));
        manager.issue(KEY, "654321");
        assertThat(store.raw(KEY).getFailedAttempts()).isZero();
        assertThat(manager.verify(KEY, "654321", true)).isTrue();
    }

    @Test
    void expiredOtpIsRejected() {
        manager.issue(KEY, OTP);
        clock.advance(Duration.ofMinutes(5).plusSeconds(1));
        assertThat(manager.verify(KEY, OTP, true)).isFalse();
    }

    @Test
    void unknownKeyAndNullOtpAreRejectedWithoutError() {
        assertThat(manager.verify("nobody", OTP, true)).isFalse();
        manager.issue(KEY, OTP);
        assertThat(manager.verify(KEY, null, true)).isFalse();
    }

    @Test
    void peekDoesNotConsumeButWrongPeekStillCountsAttempts() {
        manager.issue(KEY, OTP);
        assertThat(manager.verify(KEY, OTP, false)).isTrue();
        assertThat(manager.verify(KEY, "000000", false)).isFalse();
        assertThat(store.raw(KEY).getFailedAttempts()).isEqualTo(1);
        assertThat(manager.verify(KEY, OTP, true)).isTrue();
    }

    @Test
    void resendWithinCooldownIsRejectedWith429() {
        manager.issue(KEY, OTP);
        clock.advance(Duration.ofSeconds(10));
        assertThatThrownBy(() -> manager.issue(KEY, "654321"))
                .isInstanceOfSatisfying(UamException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(e.getMessage()).isEqualTo(OtpConstants.TOO_MANY_REQUESTS_MESSAGE);
                });
        assertThat(manager.verify(KEY, OTP, true)).as("rejected resend keeps the old OTP").isTrue();
    }

    @Test
    void sixthSendInAnHourIsRejectedAndTheWindowThenResets() {
        for (int i = 0; i < 5; i++) {
            manager.issue(KEY, OTP);
            clock.advance(Duration.ofSeconds(31));
        }
        assertThatThrownBy(() -> manager.issue(KEY, OTP))
                .isInstanceOfSatisfying(UamException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));

        clock.advance(Duration.ofHours(1));
        manager.issue(KEY, OTP);
        assertThat(store.raw(KEY).getSendCount()).isEqualTo(1);
    }

    @Test
    void sendCountersSurviveConsumption() {
        manager.issue(KEY, OTP);
        manager.verify(KEY, OTP, true);
        assertThat(store.raw(KEY).getSendCount()).isEqualTo(1);
        clock.advance(Duration.ofSeconds(31));
        manager.issue(KEY, OTP);
        assertThat(store.raw(KEY).getSendCount()).isEqualTo(2);
    }

    @Test
    void targetsAreThrottledIndependently() {
        manager.issue(KEY, OTP);
        manager.issue("CUSTOMER:PHONE:9123456780", OTP);
        assertThat(manager.verify("CUSTOMER:PHONE:9123456780", OTP, true)).isTrue();
    }

    @Test
    void concurrentVerifiesConsumeTheOtpExactlyOnce() throws Exception {
        manager.issue(KEY, OTP);
        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                start.await();
                if (manager.verify(KEY, OTP, true)) {
                    successes.incrementAndGet();
                }
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(20, TimeUnit.SECONDS)).isTrue();
        assertThat(successes.get()).isEqualTo(1);
    }

    @Test
    void concurrentWrongGuessesAreAllCountedAndLockTheChallenge() throws Exception {
        manager.issue(KEY, OTP);
        int threads = 12;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                start.await();
                manager.verify(KEY, "000000", true);
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(20, TimeUnit.SECONDS)).isTrue();
        assertThat(manager.verify(KEY, OTP, true)).as("burst of guesses exhausted the attempts").isFalse();
    }

    @Test
    void hashIsBoundToKeySaltAndOtp() {
        String salt = OtpHasher.newSalt();
        String hash = OtpHasher.hash("a", salt, OTP);
        assertThat(OtpHasher.matches(hash, OtpHasher.hash("a", salt, OTP))).isTrue();
        assertThat(OtpHasher.matches(hash, OtpHasher.hash("b", salt, OTP))).isFalse();
        assertThat(OtpHasher.matches(hash, OtpHasher.hash("a", salt, "654321"))).isFalse();
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

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
            return now;
        }
    }
}
