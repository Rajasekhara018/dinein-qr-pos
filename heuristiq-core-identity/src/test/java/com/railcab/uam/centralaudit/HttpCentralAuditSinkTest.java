package com.heuristiq.uam.centralaudit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;

import com.heuristiq.resilient.ResilienceProfile;
import com.heuristiq.resilient.ResilientCaller;

import reactor.core.publisher.Mono;

/** The HTTP push: path, service token header, and that a rejection surfaces so the publisher keeps the batch. */
class HttpCentralAuditSinkTest {

    private static CentralAuditEvent event() {
        return new CentralAuditEvent("event-01", "DOMAIN", Instant.EPOCH, "rider-uam", null, "uam", "IDENTITY", "SUCCESS", null,
                null, null, null, null, null, null, null, null);
    }

    private static ResilientCaller caller() {
        return new ResilientCaller("audit-test", ResilienceProfile.defaults().withCallTimeout(Duration.ofSeconds(2)).withMaxAttempts(1));
    }

    @Test
    void postsTheBatchToTheInternalIngestWithTheServiceToken() {
        List<ClientRequest> requests = new ArrayList<>();
        WebClient client = WebClient.builder().baseUrl("http://audit").exchangeFunction(request -> {
            requests.add(request);
            return Mono.just(ClientResponse.create(org.springframework.http.HttpStatus.OK).build());
        }).build();

        new HttpCentralAuditSink(client, caller(), "secret-token").send(List.of(event()));

        assertThat(requests).singleElement().satisfies(r -> {
            assertThat(r.method().name()).isEqualTo("POST");
            assertThat(r.url().getPath()).isEqualTo("/api/v1/audit/internal/events");
            assertThat(r.headers().getFirst("X-Rider-Service-Token")).isEqualTo("secret-token");
        });
    }

    @Test
    void aRejectedBatchThrows() {
        WebClient client = WebClient.builder().baseUrl("http://audit").exchangeFunction(
                request -> Mono.just(ClientResponse.create(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE).build())).build();

        assertThatThrownBy(() -> new HttpCentralAuditSink(client, caller(), "t").send(List.of(event())))
                .isInstanceOf(RuntimeException.class);
    }
}
