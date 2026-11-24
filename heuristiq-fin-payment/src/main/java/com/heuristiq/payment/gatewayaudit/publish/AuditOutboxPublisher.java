package com.heuristiq.payment.gatewayaudit.publish;

import com.heuristiq.payment.gatewayaudit.GatewayAuditConstants;
import com.heuristiq.payment.gatewayaudit.model.PaymentAuditOutboxEntry;
import com.heuristiq.payment.gatewayaudit.repository.PaymentAuditOutboxRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Drains {@code payment_audit_outbox} to the audit service in batches. Not transactional: rows are read,
 * sent (no database transaction open during the HTTP call) and then marked in short statements. A failed
 * batch is kept and retried with exponential backoff; an audit outage therefore never affects payments.
 * Delivery is at-least-once and the audit service dedupes on {@code eventId}.
 */
@Slf4j
@Component
public class AuditOutboxPublisher {

    private final PaymentAuditOutboxRepository outbox;
    private final AuditEventSink sink;
    private final AuditPublishProperties props;
    private final Clock clock;
    private final Counter published;
    private final Counter publishFailures;

    public AuditOutboxPublisher(PaymentAuditOutboxRepository outbox, AuditEventSink sink, AuditPublishProperties props,
                                Clock clock, MeterRegistry meters) {
        this.outbox = outbox;
        this.sink = sink;
        this.props = props;
        this.clock = clock;
        this.published = meters.counter(GatewayAuditConstants.METRIC_PUBLISHED);
        this.publishFailures = meters.counter(GatewayAuditConstants.METRIC_PUBLISH_FAILURES);
    }

    /** Sends up to {@code maxBatchesPerRun} batches; stops at the first failure or when nothing is due. Never throws. */
    public int drain() {
        if (!props.isEnabled()) {
            return 0;
        }
        int sent = 0;
        try {
            for (int i = 0; i < props.getMaxBatchesPerRun(); i++) {
                int n = drainBatch();
                if (n <= 0) {
                    break;
                }
                sent += n;
                if (n < props.getBatchSize()) {
                    break;
                }
            }
        } catch (RuntimeException e) {
            publishFailures.increment();
            log.warn("Audit outbox drain failed; pending events are kept: {}", e.getClass().getSimpleName());
        }
        return sent;
    }

    /** One batch: returns the number delivered, 0 when nothing was due, -1 when delivery failed. */
    int drainBatch() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<PaymentAuditOutboxEntry> due = outbox.findDue(now, PageRequest.of(0, props.getBatchSize()));
        if (due.isEmpty()) {
            return 0;
        }
        List<Long> ids = due.stream().map(PaymentAuditOutboxEntry::getId).toList();
        try {
            AuditEventSink.IngestResult result = sink.send(due.stream().map(PaymentAuditOutboxEntry::getPayload).toList());
            outbox.markPublished(ids, LocalDateTime.now(clock));
            published.increment(ids.size());
            if (result.rejected() > 0) {
                log.warn("Audit service rejected {} of {} payment-gateway events; they are not retried", result.rejected(), ids.size());
            }
            return ids.size();
        } catch (RuntimeException e) {
            publishFailures.increment();
            int attempts = due.stream().mapToInt(PaymentAuditOutboxEntry::getAttempts).max().orElse(0) + 1;
            long delay = Math.min(props.getMaxBackoffSeconds(), 1L << Math.min(attempts, 20));
            outbox.markFailed(ids, LocalDateTime.now(clock).plusSeconds(delay));
            log.warn("Audit service unavailable; {} events kept for retry in {}s (attempt {}): {}",
                    ids.size(), delay, attempts, e.getClass().getSimpleName());
            return -1;
        }
    }

    /** Deletes delivered rows older than the retention window; returns the number removed. Never throws. */
    public int purge() {
        try {
            return outbox.deletePublishedBefore(LocalDateTime.now(clock).minusDays(props.getPublishedRetentionDays()));
        } catch (RuntimeException e) {
            log.warn("Audit outbox purge failed: {}", e.getClass().getSimpleName());
            return 0;
        }
    }
}
