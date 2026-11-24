package com.railcab.nms.adapters.out.integration.uam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;

import com.railcab.nms.application.port.out.AudienceResolutionException;
import com.railcab.nms.domain.model.BroadcastSegment;
import com.railcab.nms.infrastructure.config.NmsProperties;
import com.heuristiq.resilient.ResilienceProfile;
import com.heuristiq.resilient.ResilientCaller;

import reactor.core.publisher.Mono;

class UamUserClientTest {

    private static ResilientCaller fastCaller() {
        return new ResilientCaller("uam-test", new ResilienceProfile(Duration.ofSeconds(2), 3,
                Duration.ofMillis(5), 2.0, 0.5, Duration.ofMillis(20),
                10, Duration.ZERO, 20, 10, 50f, Duration.ofSeconds(5), 3));
    }

    private static Mono<ClientResponse> json(HttpStatus status, String body) {
        return Mono.just(ClientResponse.create(status)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE).body(body).build());
    }

    private static String page(String userId, boolean last) {
        return "{\"success\":true,\"responseObject\":{\"content\":[{\"id\":\"" + userId
                + "\",\"roles\":[\"CUSTOMER\"],\"active\":true}],\"last\":" + last + "}}";
    }

    private static UamUserClient client(ExchangeFunction ex, int maxPages) {
        NmsProperties props = new NmsProperties();
        props.getBroadcast().setMaxPagesPerSweep(maxPages);
        props.getBroadcast().setPageSize(1);
        UamServiceTokenProvider tokens = mock(UamServiceTokenProvider.class);
        when(tokens.mintServiceToken()).thenReturn("t");
        return new UamUserClient(props, WebClient.builder().exchangeFunction(ex).build(), tokens, fastCaller());
    }

    private static ExchangeFunction pages(IntFunction<Mono<ClientResponse>> byPage, AtomicInteger calls) {
        return req -> {
            calls.incrementAndGet();
            String q = req.url().getQuery();
            int p = Integer.parseInt(q.replaceAll(".*page=(\\d+).*", "$1"));
            return byPage.apply(p);
        };
    }

    @Test
    void collectsEveryPage() {
        AtomicInteger calls = new AtomicInteger();
        UamUserClient c = client(pages(p -> json(HttpStatus.OK, page("u" + p, p == 1)), calls), 5);
        assertThat(c.resolveSegment(BroadcastSegment.USERS)).extracting("id").containsExactly("u0", "u1");
    }

    @Test
    void aTransientPageFailureIsRetriedAndTheAudienceStaysComplete() {
        AtomicInteger page1Hits = new AtomicInteger();
        AtomicInteger calls = new AtomicInteger();
        UamUserClient c = client(pages(p -> {
            if (p == 1 && page1Hits.incrementAndGet() < 2) return json(HttpStatus.SERVICE_UNAVAILABLE, "{}");
            return json(HttpStatus.OK, page("u" + p, p == 1));
        }, calls), 5);
        assertThat(c.resolveSegment(BroadcastSegment.ALL)).hasSize(2);
        assertThat(page1Hits.get()).isEqualTo(2);
    }

    @Test
    void aPageThatKeepsFailingFailsTheResolutionInsteadOfReturningAPartialAudience() {
        AtomicInteger calls = new AtomicInteger();
        UamUserClient c = client(pages(p -> p == 0 ? json(HttpStatus.OK, page("u0", false))
                : json(HttpStatus.BAD_GATEWAY, "{}"), calls), 5);
        assertThatThrownBy(() -> c.resolveSegment(BroadcastSegment.ALL))
                .isInstanceOf(AudienceResolutionException.class)
                .hasMessageContaining("page 1");
    }

    @Test
    void aClientErrorIsNotRetriedAndFailsTheResolution() {
        AtomicInteger calls = new AtomicInteger();
        UamUserClient c = client(pages(p -> json(HttpStatus.FORBIDDEN, "{}"), calls), 5);
        assertThatThrownBy(() -> c.resolveSegment(BroadcastSegment.ALL)).isInstanceOf(AudienceResolutionException.class);
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void anUnsuccessfulEnvelopeFailsTheResolution() {
        AtomicInteger calls = new AtomicInteger();
        UamUserClient c = client(pages(p -> json(HttpStatus.OK, "{\"success\":false}"), calls), 5);
        assertThatThrownBy(() -> c.resolveSegment(BroadcastSegment.ALL)).isInstanceOf(AudienceResolutionException.class);
    }

    @Test
    void exceedingTheSweepPageLimitFailsInsteadOfTruncating() {
        AtomicInteger calls = new AtomicInteger();
        UamUserClient c = client(pages(p -> json(HttpStatus.OK, page("u" + p, false)), calls), 2);
        assertThatThrownBy(() -> c.resolveSegment(BroadcastSegment.ALL))
                .isInstanceOf(AudienceResolutionException.class)
                .hasMessageContaining("truncated");
    }
}
