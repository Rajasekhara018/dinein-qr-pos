package com.heuristiq.resilient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

class ResilientCallerTest {

    private static ResilienceProfile fast() {
        return new ResilienceProfile(Duration.ofMillis(200), 3, Duration.ofMillis(5), 2.0, 0.5,
                Duration.ofMillis(20), 2, Duration.ZERO, 10, 4, 50f, Duration.ofSeconds(30), 2);
    }

    private static WebClientResponseException status(HttpStatus s) {
        return WebClientResponseException.create(s.value(), s.name(), null, null, null);
    }

    @Test
    void safeCallRetriesTransientFailureThenSucceeds() {
        ResilientCaller caller = new ResilientCaller("t1", fast());
        AtomicInteger attempts = new AtomicInteger();
        String out = caller.callBlocking(() -> attempts.incrementAndGet() < 3
                ? Mono.error(status(HttpStatus.SERVICE_UNAVAILABLE)) : Mono.just("ok"), Idempotency.SAFE);
        assertThat(out).isEqualTo("ok");
        assertThat(attempts).hasValue(3);
    }

    @Test
    void unsafeCallIsNeverRetried() {
        ResilientCaller caller = new ResilientCaller("t2", fast());
        AtomicInteger attempts = new AtomicInteger();
        assertThatThrownBy(() -> caller.callBlocking(() -> {
            attempts.incrementAndGet();
            return Mono.<String>error(status(HttpStatus.SERVICE_UNAVAILABLE));
        }, Idempotency.UNSAFE)).isInstanceOf(DownstreamUnavailableException.class);
        assertThat(attempts).hasValue(1);
    }

    @Test
    void clientErrorPassesThroughUnretriedAndDoesNotTripBreaker() {
        ResilientCaller caller = new ResilientCaller("t3", fast());
        AtomicInteger attempts = new AtomicInteger();
        for (int i = 0; i < 12; i++) {
            assertThatThrownBy(() -> caller.callBlocking(() -> {
                attempts.incrementAndGet();
                return Mono.<String>error(status(HttpStatus.NOT_FOUND));
            }, Idempotency.SAFE)).isInstanceOf(WebClientResponseException.NotFound.class);
        }
        assertThat(attempts).hasValue(12);
        assertThat(caller.circuitBreaker().getState()).isEqualTo(io.github.resilience4j.circuitbreaker.CircuitBreaker.State.CLOSED);
    }

    @Test
    void plainServerErrorIsNotRetriedButCountsAsFailure() {
        ResilientCaller caller = new ResilientCaller("t4", fast());
        AtomicInteger attempts = new AtomicInteger();
        assertThatThrownBy(() -> caller.callBlocking(() -> {
            attempts.incrementAndGet();
            return Mono.<String>error(status(HttpStatus.INTERNAL_SERVER_ERROR));
        }, Idempotency.SAFE)).isInstanceOf(DownstreamUnavailableException.class);
        assertThat(attempts).hasValue(1);
    }

    @Test
    void slowCallTimesOutWithTimeoutReason() {
        ResilientCaller caller = new ResilientCaller("t5", fast().withMaxAttempts(1));
        assertThatThrownBy(() -> caller.callBlocking(() -> Mono.never(), Idempotency.UNSAFE))
                .isInstanceOfSatisfying(DownstreamUnavailableException.class,
                        e -> assertThat(e.reason()).isEqualTo(DownstreamUnavailableException.Reason.TIMEOUT));
    }

    @Test
    void breakerOpensAfterRepeatedFailuresAndShortCircuits() {
        ResilientCaller caller = new ResilientCaller("t6", fast().withMaxAttempts(1));
        AtomicInteger attempts = new AtomicInteger();
        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> caller.callBlocking(() -> {
                attempts.incrementAndGet();
                return Mono.<String>error(status(HttpStatus.BAD_GATEWAY));
            }, Idempotency.SAFE)).isInstanceOf(DownstreamUnavailableException.class);
        }
        assertThatThrownBy(() -> caller.callBlocking(() -> {
            attempts.incrementAndGet();
            return Mono.just("never reached");
        }, Idempotency.SAFE)).isInstanceOfSatisfying(DownstreamUnavailableException.class,
                e -> assertThat(e.reason()).isEqualTo(DownstreamUnavailableException.Reason.CIRCUIT_OPEN));
        assertThat(attempts).hasValue(4);
    }

    @Test
    void bulkheadRejectsCallsBeyondTheConcurrencyLimit() {
        ResilientCaller caller = new ResilientCaller("t7", fast().withMaxAttempts(1).withCallTimeout(Duration.ofSeconds(5)));
        Mono<String> a = caller.call(Mono::never, Idempotency.UNSAFE);
        Mono<String> b = caller.call(Mono::never, Idempotency.UNSAFE);
        var s1 = a.subscribe(x -> { }, e -> { });
        var s2 = b.subscribe(x -> { }, e -> { });
        try {
            assertThatThrownBy(() -> caller.callBlocking(() -> Mono.just("x"), Idempotency.UNSAFE))
                    .isInstanceOfSatisfying(DownstreamUnavailableException.class,
                            e -> assertThat(e.reason()).isEqualTo(DownstreamUnavailableException.Reason.OVERLOADED));
        } finally {
            s1.dispose();
            s2.dispose();
        }
    }
}
