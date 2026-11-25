package com.heuristiq.identity.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.heuristiq.resilient.ResilienceProfile;
import com.heuristiq.resilient.ResilientCaller;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

class NmsNotificationServiceResilienceTest {

    private static ResilientCaller fastCaller() {
        ResilienceProfile p = new ResilienceProfile(Duration.ofSeconds(2), 3,
                Duration.ofMillis(5), 2.0, 0.5, Duration.ofMillis(20),
                10, Duration.ZERO, 20, 10, 50f, Duration.ofSeconds(5), 3);
        return new ResilientCaller("nms-test", p);
    }

    private static NmsNotificationService service(ExchangeFunction ex) {
        return new NmsNotificationService("http://nms.invalid", "key",
                WebClient.builder().exchangeFunction(ex), fastCaller());
    }

    private static Mono<ClientResponse> respond(HttpStatus status) {
        return Mono.just(ClientResponse.create(status).header("Content-Type", "text/plain").body("x").build());
    }

    @Test
    void transientFailureIsRetriedUntilSuccess() {
        AtomicInteger calls = new AtomicInteger();
        ExchangeFunction ex = (ClientRequest req) -> {
            return calls.incrementAndGet() < 3 ? respond(HttpStatus.SERVICE_UNAVAILABLE) : respond(HttpStatus.OK);
        };
        service(ex).sendOtp("a@b.com", null, "A", "123456", 5, "LOGIN");
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    void clientErrorIsNotRetriedAndDoesNotPropagate() {
        AtomicInteger calls = new AtomicInteger();
        ExchangeFunction ex = req -> {
            calls.incrementAndGet();
            return respond(HttpStatus.BAD_REQUEST);
        };
        service(ex).sendOtp("a@b.com", null, "A", "123456", 5, "LOGIN"); // must not throw
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void persistentOutageGivesUpAfterMaxAttemptsWithoutThrowing() {
        AtomicInteger calls = new AtomicInteger();
        ExchangeFunction ex = req -> {
            calls.incrementAndGet();
            return respond(HttpStatus.BAD_GATEWAY);
        };
        service(ex).sendAccountCreated("a@b.com", "A", "999");
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    void maskKeepsOnlyAPrefixOfTheAddress() {
        assertThat(NmsNotificationService.mask("nagi@example.com")).isEqualTo("na***@example.com");
        assertThat(NmsNotificationService.mask(null)).isEqualTo("n/a");
    }
}
