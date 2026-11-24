package com.railcab.dms.centralaudit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Parks an audit event in the outbox; sending happens later, off the request path ({@link AuditOutboxDrainer}). Never
 * waits for the audit service and never throws: a failure here is logged and the document operation carries on.
 */
@Component
public class CentralAuditPublisher {

    private static final Logger log = LoggerFactory.getLogger(CentralAuditPublisher.class);

    private final AuditOutboxStore outbox;
    private final ObjectMapper objectMapper;
    private final AuditPublishProperties properties;
    private final Clock clock;

    public CentralAuditPublisher(AuditOutboxStore outbox, ObjectMapper objectMapper, AuditPublishProperties properties,
                                 Clock clock) {
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Queues the event. Does nothing when publishing is disabled or the event has no organization (the audit screen
     * separates organizations, so an event without one could not be shown to anyone).
     */
    public void publish(DmsAuditEvent event) {
        if (!properties.enabled() || event == null || !StringUtils.hasText(event.eventId())) {
            return;
        }
        if (!StringUtils.hasText(event.tenantId())) {
            log.info("dms.audit.skipped eventId={} reason=no_organization", event.eventId());
            return;
        }
        try {
            Instant now = clock.instant();
            boolean stored = outbox.insertIfAbsent(new OutboxEntry(null, event.eventId(),
                    objectMapper.writeValueAsString(event), OutboxStatus.PENDING, 0, now, null, null, null, now, null,
                    now.plus(properties.outboxRetentionDays(), ChronoUnit.DAYS)));
            if (!stored) {
                log.debug("dms.audit.already-queued eventId={}", event.eventId());
            }
        } catch (Exception e) {
            // Deliberate degrade: audit is best-effort next to the operation, never a reason to fail it.
            log.warn("dms.audit.queue-failed eventId={} reason={}", event.eventId(), e.getMessage());
        }
    }
}
