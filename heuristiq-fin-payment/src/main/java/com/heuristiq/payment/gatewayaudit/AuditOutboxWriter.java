package com.heuristiq.paymentservice.gatewayaudit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.heuristiq.paymentservice.gatewayaudit.model.PaymentAuditOutboxEntry;
import com.heuristiq.paymentservice.gatewayaudit.repository.PaymentAuditOutboxRepository;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Serialises a {@link GatewayAuditEvent} into the audit service's ingest shape and stores it in the
 * audit outbox. It joins the caller's transaction when there is one, so the event commits or rolls
 * back together with the call log (or webhook ledger) row it describes.
 */
@Component
public class AuditOutboxWriter {

    private final PaymentAuditOutboxRepository outbox;
    private final ObjectMapper mapper;
    private final Clock clock;

    public AuditOutboxWriter(PaymentAuditOutboxRepository outbox, ObjectMapper mapper, Clock clock) {
        this.outbox = outbox;
        this.mapper = mapper;
        this.clock = clock;
    }

    /** Stores the event as pending. Throws on a serialisation or database failure; callers decide how to degrade. */
    public void enqueue(GatewayAuditEvent event) {
        LocalDateTime now = LocalDateTime.now(clock);
        outbox.save(PaymentAuditOutboxEntry.builder()
                .eventId(event.eventId())
                .payload(toJson(event))
                .createdAt(now)
                .nextAttemptAt(now)
                .attempts(0)
                .build());
    }

    /** The event in the audit ingest shape; deterministic for the same event. */
    public String toJson(GatewayAuditEvent event) {
        ObjectNode node = mapper.createObjectNode();
        node.put("eventId", event.eventId());
        node.put("eventType", GatewayAuditConstants.EVENT_TYPE);
        node.put("occurredAt", event.occurredAt().toString());
        node.put("sourceService", GatewayAuditConstants.SOURCE_SERVICE);
        node.put("userType", GatewayAuditConstants.USER_TYPE_SERVICE);
        node.put("appChannel", GatewayAuditConstants.CHANNEL_SERVICE);
        node.put("feature", GatewayAuditConstants.FEATURE);
        node.put("module", GatewayAuditConstants.MODULE);
        node.put("outcome", event.outcome());
        if (event.statusCode() != null) {
            node.put("statusCode", event.statusCode());
        }
        if (event.durationMs() != null) {
            node.put("durationMs", event.durationMs());
        }
        if (event.tenantId() != null && !event.tenantId().isBlank()) {
            node.put("tenantId", event.tenantId());
        }
        if (event.correlationId() != null && !event.correlationId().isBlank()) {
            node.put("correlationId", event.correlationId());
        }
        ObjectNode details = node.putObject("details");
        for (Map.Entry<String, Object> e : event.details().entrySet()) {
            Object v = e.getValue();
            if (v == null) {
                continue;
            }
            if (v instanceof Number n) {
                details.putPOJO(e.getKey(), n);
            } else if (v instanceof Boolean b) {
                details.put(e.getKey(), b);
            } else {
                details.put(e.getKey(), String.valueOf(v));
            }
        }
        try {
            return mapper.writeValueAsString(node);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot serialise audit event " + event.eventId(), ex);
        }
    }
}
