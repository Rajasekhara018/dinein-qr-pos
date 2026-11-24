package com.heuristiq.notification.application.usecase.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.heuristiq.notification.application.dto.ChannelSendOutcome;
import com.heuristiq.notification.application.dto.ChannelSendOutcome.AttemptResult;
import com.heuristiq.notification.application.port.out.ProviderSendResult;
import com.heuristiq.notification.domain.model.NotificationChannel;

import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;

/** Every provider call becomes its own attempt result, including failures, exceptions and refused calls. */
class ChannelResilienceDecoratorAttemptsTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-06T10:00:00Z"), ZoneOffset.UTC);

    private ChannelResilienceDecorator decorator;

    @BeforeEach
    void setUp() {
        decorator = new ChannelResilienceDecorator(
                CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
                        .slidingWindowSize(4).minimumNumberOfCalls(4).failureRateThreshold(50)
                        .waitDurationInOpenState(Duration.ofSeconds(30)).build()),
                RetryRegistry.of(RetryConfig.custom().maxAttempts(3).waitDuration(Duration.ofMillis(1)).build()),
                BulkheadRegistry.of(BulkheadConfig.custom().maxConcurrentCalls(10).build()),
                CLOCK);
    }

    @Test
    void aProviderThatKeepsFailingGivesOneAttemptPerRetry() {
        ChannelSendOutcome outcome = decorator.callDetailed(NotificationChannel.SMS,
                () -> ProviderSendResult.failed("digimile", "DIGIMILE_1707", "Digimile response code 1707"));

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.attempts()).isEqualTo(3);
        assertThat(outcome.attemptResults()).extracting(AttemptResult::attemptNumber).containsExactly(1, 2, 3);
        assertThat(outcome.attemptResults()).allSatisfy(a -> {
            assertThat(a.success()).isFalse();
            assertThat(a.providerName()).isEqualTo("digimile");
            assertThat(a.errorCode()).isEqualTo("DIGIMILE_1707");
            assertThat(a.errorMessage()).isEqualTo("Digimile response code 1707");
            assertThat(a.startedAt()).isNotNull();
        });
        assertThat(outcome.error()).isEqualTo("Digimile response code 1707");
    }

    @Test
    void aRetryThatSucceedsKeepsTheFailedAttemptAndTheProviderMessageId() {
        AtomicInteger calls = new AtomicInteger();

        ChannelSendOutcome outcome = decorator.callDetailed(NotificationChannel.SMS,
                () -> calls.incrementAndGet() < 2
                        ? ProviderSendResult.failed("twilio", ProviderSendResult.CODE_EXCEPTION, "timeout")
                        : ProviderSendResult.ok("twilio", "SM123", "queued"));

        assertThat(outcome.success()).isTrue();
        assertThat(outcome.attemptResults()).hasSize(2);
        assertThat(outcome.attemptResults().get(0).success()).isFalse();
        assertThat(outcome.attemptResults().get(1).success()).isTrue();
        assertThat(outcome.attemptResults().get(1).providerMessageId()).isEqualTo("SM123");
        assertThat(outcome.attemptResults().get(1).providerStatus()).isEqualTo("queued");
    }

    @Test
    void aThrowingProviderIsRecordedWithASanitisedReason() {
        ChannelSendOutcome outcome = decorator.callDetailed(NotificationChannel.EMAIL, () -> {
            throw new IllegalStateException("connect failed https://x.example/send?password=hunter2&to=a@b.com");
        });

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.attemptResults()).hasSize(3);
        assertThat(outcome.attemptResults()).allSatisfy(a -> {
            assertThat(a.errorCode()).isEqualTo(ProviderSendResult.CODE_EXCEPTION);
            assertThat(a.errorMessage()).doesNotContain("hunter2").doesNotContain("a@b.com");
        });
        assertThat(outcome.error()).doesNotContain("hunter2");
    }

    @Test
    void anOpenCircuitIsRecordedAsARefusedAttemptWithoutCallingTheProvider() {
        for (int i = 0; i < 2; i++) {
            decorator.callDetailed(NotificationChannel.WHATSAPP,
                    () -> ProviderSendResult.failed("meta", ProviderSendResult.CODE_REJECTED, null));
        }
        AtomicInteger calls = new AtomicInteger();

        ChannelSendOutcome outcome = decorator.callDetailed(NotificationChannel.WHATSAPP, () -> {
            calls.incrementAndGet();
            return ProviderSendResult.ok("meta", "wamid", null);
        });

        assertThat(calls.get()).isZero();
        assertThat(outcome.success()).isFalse();
        assertThat(outcome.error()).isEqualTo("Provider circuit open");
        AttemptResult refused = outcome.attemptResults().get(outcome.attemptResults().size() - 1);
        assertThat(refused.errorCode()).isEqualTo("CIRCUIT_OPEN");
        assertThat(refused.latencyMs()).isZero();
    }

    @Test
    void theBooleanFormStillWorksAndAlsoReportsAttempts() {
        ChannelSendOutcome outcome = decorator.call(NotificationChannel.FCM_PUSH, () -> false);

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.error()).isEqualTo("Provider returned false");
        assertThat(outcome.attemptResults()).hasSize(3);
    }
}
