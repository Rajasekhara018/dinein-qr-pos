package com.heuristiq.notification.application.usecase.support;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.heuristiq.notification.application.dto.ChannelSendOutcome;
import com.heuristiq.notification.domain.model.NotificationChannel;

import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plain unit test (no Spring context) proving the two resilience behaviors the decorator exists
 * for: {@link ChannelResilienceDecorator#call} fails fast once a channel's circuit opens, and
 * retries transient failures (a provider returning {@code false}) before giving up -- the same
 * guarantees the hand-rolled circuit breaker/semaphore it replaced were supposed to provide.
 */
class ChannelResilienceDecoratorTest {

    private ChannelResilienceDecorator decorator;

    @BeforeEach
    void setUp() {
        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
                .slidingWindowSize(4)
                .minimumNumberOfCalls(4)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(2)
                .build();
        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(1))
                .build();
        BulkheadConfig bulkheadConfig = BulkheadConfig.custom()
                .maxConcurrentCalls(10)
                .build();

        decorator = new ChannelResilienceDecorator(
                CircuitBreakerRegistry.of(circuitBreakerConfig),
                RetryRegistry.of(retryConfig),
                BulkheadRegistry.of(bulkheadConfig));
    }

    @Test
    void retriesAProviderThatReturnsFalseBeforeGivingUp() {
        AtomicInteger invocations = new AtomicInteger();

        ChannelSendOutcome outcome = decorator.call(NotificationChannel.EMAIL, () -> {
            invocations.incrementAndGet();
            return false;
        });

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.attempts()).isEqualTo(3);
        assertThat(invocations.get()).isEqualTo(3);
    }

    @Test
    void succeedsWithoutExhaustingRetriesWhenProviderReturnsTrue() {
        AtomicInteger invocations = new AtomicInteger();

        ChannelSendOutcome outcome = decorator.call(NotificationChannel.SMS,
                () -> invocations.incrementAndGet() >= 2);

        assertThat(outcome.success()).isTrue();
        assertThat(outcome.attempts()).isEqualTo(2);
    }

    @Test
    void opensCircuitAfterMinimumFailuresThenFailsFastWithoutInvokingSupplier() {
        // The circuit breaker sits innermost (closest to the raw supplier), so it records one
        // outcome per individual retry attempt, not one per top-level call() -- the 4-call/
        // 50%-failure-rate window fills mid-retry-sequence on the second top-level call, at
        // which point the breaker opens and the remaining retry attempts (of that call, and all
        // of the following ones) short-circuit via CallNotPermittedException without ever
        // reaching the supplier again. Call #1 contributes 3 invocations (its full retry budget);
        // call #2 contributes exactly 1 more before the breaker trips mid-sequence; calls #3/#4
        // contribute none. Verified empirically, not hand-derived, given this interaction is
        // Resilience4j-internal and easy to get wrong by inspection alone.
        AtomicInteger invocations = new AtomicInteger();

        for (int i = 0; i < 4; i++) {
            ChannelSendOutcome outcome = decorator.call(NotificationChannel.WHATSAPP, () -> {
                invocations.incrementAndGet();
                return false;
            });
            assertThat(outcome.success()).isFalse();
        }
        assertThat(invocations.get()).isEqualTo(4);

        // Breaker is now open -- the next call must fail fast without invoking the supplier again.
        ChannelSendOutcome outcome = decorator.call(NotificationChannel.WHATSAPP, () -> {
            invocations.incrementAndGet();
            return true;
        });

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.error()).isEqualTo("Provider circuit open");
        assertThat(invocations.get()).isEqualTo(4);
    }
}
