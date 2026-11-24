package com.heuristiq.paymentservice.gatewayaudit.publish;

import com.fasterxml.jackson.databind.JsonNode;
import com.heuristiq.resilient.Idempotency;
import com.heuristiq.resilient.ResilientCaller;
import com.heuristiq.paymentservice.gatewayaudit.GatewayAuditConstants;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

/**
 * Posts batches to {@code POST /api/v1/audit/internal/events} through the shared
 * {@link ResilientCaller} (timeout, circuit breaker, bulkhead, retry). Ingest is idempotent on
 * {@code eventId}, hence {@link Idempotency#SAFE}.
 */
public class ResilientAuditEventSink implements AuditEventSink {

    private final WebClient auditWebClient;
    private final ResilientCaller auditResilientCaller;

    public ResilientAuditEventSink(WebClient auditWebClient, ResilientCaller auditResilientCaller) {
        this.auditWebClient = auditWebClient;
        this.auditResilientCaller = auditResilientCaller;
    }

    @Override
    public IngestResult send(List<String> eventJson) {
        String body = "{\"events\":[" + String.join(",", eventJson) + "]}";
        JsonNode reply = auditResilientCaller.callBlocking(() -> auditWebClient.post()
                .uri(GatewayAuditConstants.INGEST_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class), Idempotency.SAFE);
        return parse(reply);
    }

    /** The counts may sit at the top level or inside a {@code responseObject} envelope. */
    static IngestResult parse(JsonNode reply) {
        if (reply == null) {
            return new IngestResult(0, 0, 0, 0);
        }
        JsonNode n = reply.has("responseObject") && reply.get("responseObject").isObject() ? reply.get("responseObject") : reply;
        return new IngestResult(n.path("received").asInt(0), n.path("accepted").asInt(0),
                n.path("duplicates").asInt(0), n.path("rejected").asInt(0));
    }
}
