package com.railcab.dms.centralaudit;

import com.heuristiq.resilient.Idempotency;
import com.heuristiq.resilient.ResilientCaller;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

/**
 * Posts batches of events to the central audit service ({@code POST /api/v1/audit/internal/events}, service token)
 * through the shared {@link ResilientCaller} (timeout, bulkhead, circuit breaker). Marked {@link Idempotency#SAFE}
 * because the audit service stores an event once per {@code eventId}. A failure surfaces as an
 * {@link AuditEventSink.AuditSinkException}; the outbox keeps the events and tries again later.
 */
@Component
public class AuditServiceHttpClient implements AuditEventSink {

    private final AuditPublishProperties properties;
    private final WebClient client;
    private final ResilientCaller caller;

    public AuditServiceHttpClient(AuditPublishProperties properties, @Qualifier(CentralAuditConstants.AUDIT_WEB_CLIENT_BEAN) WebClient client,
                                  @Qualifier(CentralAuditConstants.AUDIT_RESILIENT_CALLER_BEAN) ResilientCaller caller) {
        this.properties = properties;
        this.client = client;
        this.caller = caller;
    }

    @Override
    public void send(List<String> eventsJson) {
        if (eventsJson == null || eventsJson.isEmpty()) {
            return;
        }
        String body = CentralAuditConstants.EVENTS_PREFIX + String.join(",", eventsJson) + CentralAuditConstants.EVENTS_SUFFIX;
        try {
            caller.callBlocking(() -> client.post()
                    .uri(CentralAuditConstants.INGEST_PATH)
                    .header(CentralAuditConstants.HEADER_SERVICE_TOKEN, properties.serviceToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .toBodilessEntity(), Idempotency.SAFE);
        } catch (RuntimeException e) {
            throw new AuditSinkException("Audit service did not accept the batch: " + e.getMessage(), e);
        }
    }
}
