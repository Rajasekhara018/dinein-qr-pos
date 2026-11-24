package com.heuristiq.notification.adapters.out.integration.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;

import com.heuristiq.notification.application.port.out.AuditEventSink.AuditSinkException;
import com.heuristiq.notification.infrastructure.config.NmsProperties;
import com.heuristiq.resilient.ResilienceProfile;
import com.heuristiq.resilient.ResilientCaller;

import reactor.core.publisher.Mono;

/** The audit client posts the batch with the service token and turns every failure into AuditSinkException. */
class AuditServiceHttpClientTest {

    private NmsProperties props;
    private final AtomicReference<ClientRequest> lastRequest = new AtomicReference<>();
    private final AtomicInteger calls = new AtomicInteger();

    @BeforeEach
    void setUp() {
        props = new NmsProperties();
        props.getAudit().getPublish().setUrl("http://audit.test/");
        props.getAudit().getPublish().setServiceToken("svc-token");
    }

    private AuditServiceHttpClient client(ExchangeFunction exchange) {
        ResilienceProfile profile = ResilienceProfile.defaults().withCallTimeout(Duration.ofSeconds(2)).withMaxAttempts(2);
        return new AuditServiceHttpClient(props, WebClient.builder().exchangeFunction(exchange).build(),
                new ResilientCaller("audit-test", profile));
    }

    private ExchangeFunction respondingWith(HttpStatus status) {
        return request -> {
            calls.incrementAndGet();
            lastRequest.set(request);
            return Mono.just(ClientResponse.create(status).build());
        };
    }

    @Test
    void postsTheBatchToTheIngestPathWithTheServiceToken() {
        client(respondingWith(HttpStatus.OK)).send(List.of("{\"eventId\":\"a\"}", "{\"eventId\":\"b\"}"));

        ClientRequest request = lastRequest.get();
        assertThat(request.url().toString()).isEqualTo("http://audit.test/api/v1/audit/internal/events");
        assertThat(request.method().name()).isEqualTo("POST");
        assertThat(request.headers().getFirst("X-Rider-Service-Token")).isEqualTo("svc-token");
        assertThat(request.headers().getContentType().toString()).startsWith("application/json");
    }

    @Test
    void aServerErrorIsRetriedAsTheCallIsIdempotentThenReportedAsAnOutage() {
        AuditServiceHttpClient client = client(respondingWith(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> client.send(List.of("{}"))).isInstanceOf(AuditSinkException.class);
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void aClientErrorIsNotRetriedButStillKeepsTheEvents() {
        AuditServiceHttpClient client = client(respondingWith(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> client.send(List.of("{}"))).isInstanceOf(AuditSinkException.class);
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void aConnectionFailureIsReportedAsAnOutage() {
        AuditServiceHttpClient client = client(request -> Mono.error(new java.net.ConnectException("refused")));

        assertThatThrownBy(() -> client.send(List.of("{}"))).isInstanceOf(AuditSinkException.class);
    }

    @Test
    void anEmptyBatchMakesNoCall() {
        client(respondingWith(HttpStatus.OK)).send(List.of());

        assertThat(calls.get()).isZero();
    }
}
