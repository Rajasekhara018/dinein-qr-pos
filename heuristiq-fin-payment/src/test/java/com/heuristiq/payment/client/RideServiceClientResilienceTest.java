package com.heuristiq.payment.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heuristiq.resilient.DownstreamUnavailableException;
import com.heuristiq.resilient.ResilienceProfile;
import com.heuristiq.resilient.ResilientCaller;
import com.heuristiq.payment.events.DurableEventOutbox;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/** Payment -> Ride lookups go through ResilientCaller: GET retried on 503, 404 passed through, outage surfaced. */
class RideServiceClientResilienceTest {

    private static final ResilienceProfile FAST = ResilienceProfile.defaults()
            .withCallTimeout(Duration.ofSeconds(2)).withMaxAttempts(3);

    private RideServiceClient client(AtomicInteger calls, HttpStatus... statuses) {
        WebClient webClient = WebClient.builder().exchangeFunction(request -> {
            HttpStatus s = statuses[Math.min(calls.getAndIncrement(), statuses.length - 1)];
            ClientResponse.Builder b = ClientResponse.create(s).header("Content-Type", "application/json");
            return Mono.just(s.is2xxSuccessful() ? b.body("{}").build() : b.body("err").build());
        }).build();
        return new RideServiceClient(webClient, new ResilientCaller("ride-test", FAST),
                mock(DurableEventOutbox.class), new ObjectMapper());
    }

    @Test
    void getRide_retriesATransient503_thenSucceeds() {
        AtomicInteger calls = new AtomicInteger();

        var ride = client(calls, HttpStatus.SERVICE_UNAVAILABLE, HttpStatus.OK).getRide("r1");

        assertThat(ride).isPresent();
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void getRide_404_isNotRetried_andKeepsItsOriginalType() {
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> client(calls, HttpStatus.NOT_FOUND).getRide("r1"))
                .isInstanceOf(WebClientResponseException.NotFound.class);
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void getRide_persistentOutage_becomesDownstreamUnavailable() {
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> client(calls, HttpStatus.SERVICE_UNAVAILABLE).getRide("r1"))
                .isInstanceOf(DownstreamUnavailableException.class);
        assertThat(calls.get()).isEqualTo(3);
    }

}
