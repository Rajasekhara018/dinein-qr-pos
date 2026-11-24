package com.heuristiq.gateway.audit;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AuditServiceSinkTest {

    private final List<ClientRequest> requests = new ArrayList<>();

    private AuditServiceSink sink(HttpStatus status, Duration timeout, CircuitBreaker breaker) {
        WebClient client = WebClient.builder().baseUrl("http://audit").exchangeFunction(req -> {
            requests.add(req);
            return Mono.just(ClientResponse.create(status).build());
        }).build();
        return new AuditServiceSink(client, "svc-token", timeout, breaker);
    }

    private static GatewayAuditEvent event() {
        return GatewayAuditEvent.builder().eventId("e1").method("GET").path("/api/x").build();
    }

    @Test
    void postsTheBatchWithTheServiceTokenToTheAuditEndpoint() {
        AuditServiceSink sink = sink(HttpStatus.ACCEPTED, Duration.ofSeconds(1), CircuitBreaker.ofDefaults("t1"));

        StepVerifier.create(sink.send(List.of(event()))).verifyComplete();

        assertThat(requests).hasSize(1);
        ClientRequest req = requests.get(0);
        assertThat(req.method().name()).isEqualTo("POST");
        assertThat(req.url().toString()).isEqualTo("http://audit" + AuditConstants.PATH);
        assertThat(req.headers().getFirst(AuditConstants.HEADER_SERVICE_TOKEN)).isEqualTo("svc-token");
    }

    @Test
    void errorResponseSurfacesAsErrorSoTheCallerDropsTheBatch() {
        AuditServiceSink sink = sink(HttpStatus.INTERNAL_SERVER_ERROR, Duration.ofSeconds(1), CircuitBreaker.ofDefaults("t2"));

        StepVerifier.create(sink.send(List.of(event()))).expectError().verify();
    }

    @Test
    void openCircuitRejectsWithoutCallingTheService() {
        CircuitBreaker breaker = CircuitBreaker.of("t3", CircuitBreakerConfig.ofDefaults());
        breaker.transitionToOpenState();
        AuditServiceSink sink = sink(HttpStatus.ACCEPTED, Duration.ofSeconds(1), breaker);

        StepVerifier.create(sink.send(List.of(event()))).expectError(CallNotPermittedException.class).verify();
        assertThat(requests).isEmpty();
    }

    @Test
    void emptyBatchIsStillSerialised() {
        AuditServiceSink sink = sink(HttpStatus.OK, Duration.ofSeconds(1), CircuitBreaker.ofDefaults("t4"));

        StepVerifier.create(sink.send(List.of())).verifyComplete();
        assertThat(requests).hasSize(1);
    }
}
