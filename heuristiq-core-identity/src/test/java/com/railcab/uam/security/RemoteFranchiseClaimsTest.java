package com.heuristiq.uam.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.heuristiq.resilient.DownstreamUnavailableException;
import com.heuristiq.resilient.ResilienceProfile;
import com.heuristiq.resilient.ResilientCaller;

import reactor.core.publisher.Mono;

/** The franchise claim is fetched from the franchise service, fails closed, and is empty only when the service is switched off. */
class RemoteFranchiseClaimsTest {

    private static ResilientCaller caller(int attempts) {
        return new ResilientCaller("franchise-test", ResilienceProfile.defaults()
                .withCallTimeout(Duration.ofMillis(500)).withMaxAttempts(attempts).withMaxConcurrentCalls(5));
    }

    private static ClientResponse json(HttpStatus status, String body) {
        return ClientResponse.create(status).header("Content-Type", MediaType.APPLICATION_JSON_VALUE).body(body).build();
    }

    private static RemoteFranchiseClaims claims(ExchangeFunction exchange, int attempts, boolean enabled) {
        WebClient client = WebClient.builder().baseUrl("http://franchise.test").exchangeFunction(exchange).build();
        return new RemoteFranchiseClaims(client, caller(attempts), "svc-token", enabled);
    }

    @Test
    void theFranchiseIdsComeFromTheServiceForThatOrganizationAndUserWithTheServiceToken() {
        AtomicReference<ClientRequest> sent = new AtomicReference<>();
        RemoteFranchiseClaims claims = claims(request -> {
            sent.set(request);
            return Mono.just(json(HttpStatus.OK, "{\"success\":true,\"message\":\"Success\",\"responseObject\":[\"f1\",\"f2\"],\"extra\":1}"));
        }, 1, true);

        assertThat(claims.franchiseIdsOf("org-1", "u1")).containsExactly("f1", "f2");

        assertThat(sent.get().url().getPath()).isEqualTo("/api/v1/franchise/internal/claims");
        assertThat(sent.get().url().getQuery()).contains("tenantId=org-1").contains("userId=u1");
        assertThat(sent.get().headers().getFirst("X-Rider-Service-Token")).isEqualTo("svc-token");
    }

    @Test
    void aPersonInNoFranchiseGetsAnEmptyListFromTheService() {
        RemoteFranchiseClaims claims = claims(request -> Mono.just(json(HttpStatus.OK, "{\"success\":true,\"responseObject\":[]}")), 1, true);

        assertThat(claims.franchiseIdsOf("org-1", "u1")).isEmpty();
    }

    @Test
    void whenSwitchedOffNobodyHasFranchisesAndTheServiceIsNeverCalled() {
        AtomicInteger calls = new AtomicInteger();
        RemoteFranchiseClaims claims = claims(request -> {
            calls.incrementAndGet();
            return Mono.error(new IllegalStateException("must not be called"));
        }, 1, false);

        assertThat(claims.franchiseIdsOf("org-1", "u1")).isEmpty();
        assertThat(calls).hasValue(0);
    }

    @Test
    void aServerErrorIsThrownNeverAnEmptyList() {
        RemoteFranchiseClaims claims = claims(request -> Mono.just(json(HttpStatus.INTERNAL_SERVER_ERROR, "{}")), 1, true);

        assertThatThrownBy(() -> claims.franchiseIdsOf("org-1", "u1")).isInstanceOf(DownstreamUnavailableException.class);
    }

    @Test
    void aRefusalFromTheServiceIsThrown() {
        RemoteFranchiseClaims claims = claims(request -> Mono.just(json(HttpStatus.UNAUTHORIZED, "{}")), 1, true);

        assertThatThrownBy(() -> claims.franchiseIdsOf("org-1", "u1")).isInstanceOf(DownstreamUnavailableException.class)
                .hasCauseInstanceOf(WebClientResponseException.class);
    }

    @Test
    void aServiceThatCannotBeReachedIsThrownAsUnavailable() {
        RemoteFranchiseClaims claims = claims(request -> Mono.error(new java.net.ConnectException("refused")), 1, true);

        assertThatThrownBy(() -> claims.franchiseIdsOf("org-1", "u1")).isInstanceOf(DownstreamUnavailableException.class);
    }

    @Test
    void aSlowServiceTimesOutAsUnavailable() {
        RemoteFranchiseClaims claims = claims(request -> Mono.<ClientResponse>never(), 1, true);

        assertThatThrownBy(() -> claims.franchiseIdsOf("org-1", "u1")).isInstanceOfSatisfying(DownstreamUnavailableException.class,
                e -> assertThat(e.reason()).isEqualTo(DownstreamUnavailableException.Reason.TIMEOUT));
    }

    @Test
    void anUnsuccessfulOrEmptyEnvelopeIsThrownNotReadAsNoFranchises() {
        assertThatThrownBy(() -> claims(request -> Mono.just(json(HttpStatus.OK, "{\"success\":false,\"message\":\"no\"}")), 1, true)
                .franchiseIdsOf("org-1", "u1")).isInstanceOf(DownstreamUnavailableException.class);
        assertThatThrownBy(() -> claims(request -> Mono.just(json(HttpStatus.OK, "{\"success\":true}")), 1, true)
                .franchiseIdsOf("org-1", "u1")).isInstanceOf(DownstreamUnavailableException.class);
        assertThatThrownBy(() -> claims(request -> Mono.just(json(HttpStatus.OK, "")), 1, true)
                .franchiseIdsOf("org-1", "u1")).isInstanceOf(RuntimeException.class);
    }

    @Test
    void aTransientFailureIsRetriedBecauseTheLookupIsARead() {
        AtomicInteger calls = new AtomicInteger();
        RemoteFranchiseClaims claims = claims(request -> calls.incrementAndGet() == 1
                ? Mono.just(json(HttpStatus.SERVICE_UNAVAILABLE, "{}"))
                : Mono.just(json(HttpStatus.OK, "{\"success\":true,\"responseObject\":[\"f9\"]}")), 3, true);

        assertThat(claims.franchiseIdsOf("org-1", "u1")).isEqualTo(List.of("f9"));
        assertThat(calls).hasValue(2);
    }

    @Test
    void noServiceTokenConfiguredSendsNoHeader() {
        AtomicReference<ClientRequest> sent = new AtomicReference<>();
        WebClient client = WebClient.builder().baseUrl("http://franchise.test").exchangeFunction(request -> {
            sent.set(request);
            return Mono.just(json(HttpStatus.OK, "{\"success\":true,\"responseObject\":[]}"));
        }).build();

        new RemoteFranchiseClaims(client, caller(1), "", true).franchiseIdsOf("org-1", "u1");

        assertThat(sent.get().headers().getFirst("X-Rider-Service-Token")).isNull();
    }
}
