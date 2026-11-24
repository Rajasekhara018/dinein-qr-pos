package com.heuristiq.gateway.audit;

import static com.heuristiq.gateway.audit.AuditConstants.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Posts batches to {@code POST /api/v1/audit/internal/events} of Rider_AuditService with the shared service token,
 * through a WebClient guarded by a circuit breaker and a timeout. Not retried: a failed batch is dropped by the
 * caller (audit is best effort and must never push back on user traffic). The correlation id of the batch is not
 * forwarded because a batch spans many requests.
 */
public class AuditServiceSink implements AuditEventSink {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .setSerializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL);

    private final WebClient client;
    private final String serviceToken;
    private final Duration timeout;
    private final CircuitBreaker circuitBreaker;

    /**
     * Creates the sink.
     *
     * @param client         WebClient whose base URL is the audit service (may load-balance)
     * @param serviceToken   value of {@code X-Rider-Service-Token}
     * @param timeout        per-request timeout
     * @param circuitBreaker breaker around the call
     */
    public AuditServiceSink(WebClient client, String serviceToken, Duration timeout, CircuitBreaker circuitBreaker) {
        this.client = client;
        this.serviceToken = serviceToken;
        this.timeout = timeout;
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public Mono<Void> send(List<GatewayAuditEvent> batch) {
        String json;
        try {
            json = MAPPER.writeValueAsString(Map.of("events", batch));
        } catch (Exception e) {
            return Mono.error(e);
        }
        return client.post().uri(PATH).header(HEADER_SERVICE_TOKEN, serviceToken)
                .contentType(MediaType.APPLICATION_JSON).bodyValue(json)
                .retrieve().toBodilessEntity().then()
                .timeout(timeout)
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker));
    }
}
