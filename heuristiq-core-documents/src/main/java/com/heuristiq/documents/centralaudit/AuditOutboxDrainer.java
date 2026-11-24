package com.heuristiq.documents.centralaudit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Drains the audit outbox: finds due events, leases each so only one DMS instance sends it, posts the batch to the
 * audit service and records the result. A failed batch is rescheduled with exponential backoff and marked dead after
 * {@code max-attempts}; the audit service de-duplicates on {@code eventId}, so resending a batch whose answer was lost
 * is harmless. Runs only on the scheduler thread and never throws.
 */
@Service
public class AuditOutboxDrainer {

    private static final Logger log = LoggerFactory.getLogger(AuditOutboxDrainer.class);

    private final AuditOutboxStore store;
    private final AuditEventSink sink;
    private final AuditPublishProperties properties;
    private final Clock clock;
    private final String instanceId = UUID.randomUUID().toString();
    private final AtomicBoolean tokenWarned = new AtomicBoolean(false);

    public AuditOutboxDrainer(AuditOutboxStore store, AuditEventSink sink, AuditPublishProperties properties, Clock clock) {
        this.store = store;
        this.sink = sink;
        this.properties = properties;
        this.clock = clock;
    }

    /** One drain run. @return the number of events the audit service accepted */
    public int drain() {
        if (!properties.enabled()) {
            return 0;
        }
        if (!StringUtils.hasText(properties.serviceToken())) {
            if (tokenWarned.compareAndSet(false, true)) {
                log.error("railcab.dms.audit.publish.service-token (RIDER_INTERNAL_SERVICE_TOKEN) is not set; "
                        + "audit events stay queued until it is");
            }
            return 0;
        }
        try {
            return drainOnce();
        } catch (RuntimeException e) {
            // Scheduler boundary: log with context and wait for the next run.
            log.warn("DMS audit outbox drain failed: {}", e.getMessage(), e);
            return 0;
        }
    }

    private int drainOnce() {
        Instant now = clock.instant();
        store.purgeExpired(now);
        List<OutboxEntry> leased = new ArrayList<>();
        for (OutboxEntry entry : store.findDue(now, properties.batchSize())) {
            if (store.tryLease(entry.id(), instanceId, now, now.plusSeconds(properties.leaseSeconds()))) {
                leased.add(entry);
            }
        }
        if (leased.isEmpty()) {
            return 0;
        }
        try {
            sink.send(leased.stream().map(OutboxEntry::payload).toList());
        } catch (RuntimeException e) {
            reschedule(leased, now, e);
            return 0;
        }
        Instant sentAt = clock.instant();
        leased.forEach(entry -> store.markSent(entry.id(), sentAt));
        return leased.size();
    }

    private void reschedule(List<OutboxEntry> entries, Instant now, RuntimeException cause) {
        String reason = cut(cause.getMessage());
        log.warn("Audit service did not take a batch of {} events (will retry): {}", entries.size(), reason);
        for (OutboxEntry entry : entries) {
            int attempts = entry.attempts() + 1;
            boolean dead = attempts >= properties.maxAttempts();
            store.markFailed(entry.id(), attempts, now.plusSeconds(backoffSeconds(attempts)), reason, dead);
            if (dead) {
                log.error("Audit event {} gave up after {} attempts", entry.eventId(), attempts);
            }
        }
    }

    /** Exponential backoff, doubling from the initial delay up to the configured maximum. */
    long backoffSeconds(int attempts) {
        long delay = properties.retryInitialBackoffSeconds();
        for (int i = 1; i < attempts && delay < properties.retryMaxBackoffSeconds(); i++) {
            delay *= 2;
        }
        return Math.min(delay, properties.retryMaxBackoffSeconds());
    }

    private static String cut(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= CentralAuditConstants.ERROR_MAX_LENGTH ? message
                : message.substring(0, CentralAuditConstants.ERROR_MAX_LENGTH);
    }
}
