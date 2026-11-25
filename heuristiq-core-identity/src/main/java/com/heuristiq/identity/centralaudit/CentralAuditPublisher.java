package com.heuristiq.identity.centralaudit;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Durable publisher of UAM domain audit events. {@link #publish} writes the event to the outbox (the JDBC outbox does so in its
 * own transaction, so a failed insert cannot abort the caller's) and never throws, so an audit problem cannot fail a request; {@link #flush} claims due
 * rows under a lease (safe with several UAM instances), pushes them in batches and removes them only after a 2xx. A failed
 * push keeps the rows and retries them with exponential backoff; the audit service ignores a repeated {@code eventId}.
 * The table is bounded by {@code maxRows}: over it, only rows older than the retention are dropped, with an ERROR log
 * and a metric; fresh events are never dropped.
 */
public class CentralAuditPublisher {

    private static final Logger log = LoggerFactory.getLogger(CentralAuditPublisher.class);
    /** Batches sent in one run, so one run cannot hold the scheduler thread for long. */
    private static final int MAX_BATCHES_PER_RUN = 10;
    /** Doubling stops here; the configured maximum caps the delay afterwards. */
    private static final int MAX_BACKOFF_DOUBLINGS = 20;

    private final CentralAuditProperties properties;
    private final CentralAuditOutbox outbox;
    private final CentralAuditSink sink;
    private final Clock clock;
    private final MeterRegistry metrics;

    public CentralAuditPublisher(CentralAuditProperties properties, CentralAuditOutbox outbox, CentralAuditSink sink,
                                 Clock clock, MeterRegistry metrics) {
        this.properties = properties;
        this.outbox = outbox;
        this.sink = sink;
        this.clock = clock;
        this.metrics = metrics;
    }

    /** Stores the event in the outbox; does nothing when publishing is disabled. Never throws. */
    public void publish(CentralAuditEvent event) {
        if (!properties.enabled() || event == null) {
            return;
        }
        try {
            outbox.add(event.eventId(), event.tenantId(), CentralAuditPayloadCodec.toJson(event), clock.instant());
        } catch (RuntimeException e) {
            metrics.counter(CentralAuditConstants.METRIC_INSERT_FAILED).increment();
            log.error("uam.audit.outbox-insert-failed eventId={} reason={}", event.eventId(), e.toString());
        }
    }

    /** Number of events waiting in the outbox (leased or not). */
    public long pending() {
        return outbox.count();
    }

    /** Drains due outbox rows in batches until none is due, a push fails, or the per-run limit is reached. */
    @Scheduled(fixedDelayString = "${railcab.uam.audit.publish.interval-ms:2000}",
            initialDelayString = "${railcab.uam.audit.publish.interval-ms:2000}")
    public void flush() {
        if (!properties.enabled()) {
            return;
        }
        try {
            enforceBound();
            if (!StringUtils.hasText(properties.serviceToken())) {
                return;
            }
            drain();
        } catch (RuntimeException e) {
            log.warn("uam.audit.drain-failed reason={}", e.toString());
        }
    }

    private void drain() {
        Duration lease = Duration.ofMillis(properties.leaseMs());
        for (int i = 0; i < MAX_BATCHES_PER_RUN; i++) {
            List<CentralAuditOutbox.Entry> claimed = outbox.claim(properties.batchSize(), clock.instant(), lease);
            if (claimed.isEmpty()) {
                return;
            }
            List<CentralAuditOutbox.Entry> sendable = new ArrayList<>(claimed.size());
            List<CentralAuditEvent> events = new ArrayList<>(claimed.size());
            List<String> unreadable = new ArrayList<>();
            for (CentralAuditOutbox.Entry entry : claimed) {
                try {
                    events.add(CentralAuditPayloadCodec.fromJson(entry.payload()));
                    sendable.add(entry);
                } catch (IllegalStateException e) {
                    unreadable.add(entry.eventId());
                    log.error("uam.audit.outbox-row-unreadable eventId={} tenantId={} reason={}", entry.eventId(), entry.tenantId(),
                            e.getMessage());
                }
            }
            if (!unreadable.isEmpty()) {
                // a corrupt payload can never be delivered; removing it keeps it from blocking the queue
                metrics.counter(CentralAuditConstants.METRIC_DROPPED, "reason", "unreadable").increment(unreadable.size());
                outbox.delete(unreadable);
            }
            if (!events.isEmpty() && !push(sendable, events)) {
                return;
            }
        }
    }

    private boolean push(List<CentralAuditOutbox.Entry> entries, List<CentralAuditEvent> events) {
        try {
            sink.send(events);
        } catch (RuntimeException e) {
            Instant now = clock.instant();
            for (CentralAuditOutbox.Entry entry : entries) {
                int attempts = entry.attempts() + 1;
                outbox.reschedule(entry.eventId(), attempts, now.plus(backoff(attempts)));
            }
            metrics.counter(CentralAuditConstants.METRIC_SEND_FAILED).increment();
            log.warn("uam.audit.push-failed events={} reason={}", entries.size(), e.getMessage());
            return false;
        }
        outbox.delete(entries.stream().map(CentralAuditOutbox.Entry::eventId).toList());
        return true;
    }

    private Duration backoff(int attempts) {
        long millis = properties.backoffInitialMs() << Math.min(attempts - 1, MAX_BACKOFF_DOUBLINGS);
        return Duration.ofMillis(Math.min(properties.backoffMaxMs(), Math.max(millis, properties.backoffInitialMs())));
    }

    /** Keeps the table within {@code maxRows} by dropping only rows past the retention; fresh rows are never dropped. */
    private void enforceBound() {
        long excess = outbox.count() - properties.maxRows();
        if (excess <= 0) {
            return;
        }
        Instant cutoff = clock.instant().minus(Duration.ofHours(properties.retentionHours()));
        int dropped = outbox.deleteOldest(cutoff, (int) Math.min(excess, Integer.MAX_VALUE));
        if (dropped > 0) {
            metrics.counter(CentralAuditConstants.METRIC_DROPPED, "reason", "retention").increment(dropped);
        }
        metrics.counter(CentralAuditConstants.METRIC_OVERFLOW).increment();
        log.error("uam.audit.outbox-over-limit maxRows={} excess={} droppedExpired={} retentionHours={}", properties.maxRows(),
                excess, dropped, properties.retentionHours());
    }
}
