package com.heuristiq.identity.centralaudit;

import com.heuristiq.resilient.Idempotency;
import com.heuristiq.resilient.ResilientCaller;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

/**
 * Posts batches to {@code POST /api/v1/audit/internal/events} with the service token through the shared
 * {@link ResilientCaller} (timeout, bulkhead, circuit breaker). {@link Idempotency#SAFE} because the audit service
 * stores an event once per {@code eventId}, so a retried batch cannot duplicate.
 */
public class HttpCentralAuditSink implements CentralAuditSink {

    private final WebClient client;
    private final ResilientCaller caller;
    private final String serviceToken;

    public HttpCentralAuditSink(WebClient client, ResilientCaller caller, String serviceToken) {
        this.client = client;
        this.caller = caller;
        this.serviceToken = serviceToken;
    }

    @Override
    public void send(List<CentralAuditEvent> events) {
        caller.callBlocking(() -> client.post()
                .uri(CentralAuditConstants.INGEST_PATH)
                .header(CentralAuditConstants.HEADER_SERVICE_TOKEN, serviceToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("events", events))
                .retrieve()
                .toBodilessEntity(), Idempotency.SAFE);
    }
}
