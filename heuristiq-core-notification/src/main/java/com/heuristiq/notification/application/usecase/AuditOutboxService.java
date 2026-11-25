package com.heuristiq.notification.application.usecase;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.heuristiq.notification.application.port.in.AuditOutboxUseCase;
import com.heuristiq.notification.application.port.out.AuditEventSink;
import com.heuristiq.notification.application.usecase.support.ErrorSanitizer;
import com.heuristiq.notification.domain.model.AuditOutboxEntry;
import com.heuristiq.notification.domain.repository.IAuditOutboxRepository;
import com.heuristiq.notification.infrastructure.config.NmsProperties;

/**
 * Drains the audit outbox: finds due events, leases each one so only one NMS instance sends it,
 * posts the batch to the audit service and records the result. A failed batch is rescheduled with
 * exponential backoff and marked dead after {@code maxAttempts}; the audit service de-duplicates on
 * {@code eventId}, so a batch that was accepted but whose answer was lost is harmless to resend.
 *
 * <p>Runs only on the scheduler thread, never on a request path, and never throws.
 */
@Service
public class AuditOutboxService implements AuditOutboxUseCase {

    private static final Logger logger = LoggerFactory.getLogger(AuditOutboxService.class);

    private final IAuditOutboxRepository repository;
    private final AuditEventSink sink;
    private final NmsProperties props;
    private final Clock clock;
    private final String instanceId = UUID.randomUUID().toString();
    private final AtomicBoolean tokenWarned = new AtomicBoolean(false);

    public AuditOutboxService(IAuditOutboxRepository repository, AuditEventSink sink, NmsProperties props, Clock clock) {
        this.repository = repository;
        this.sink = sink;
        this.props = props;
        this.clock = clock;
    }

    @Override
    public int drain() {
        NmsProperties.Audit.Publish cfg = props.getAudit().getPublish();
        if (!cfg.isEnabled()) {
            return 0;
        }
        if (!StringUtils.hasText(cfg.getServiceToken())) {
            if (tokenWarned.compareAndSet(false, true)) {
                logger.error("nms.audit.publish.service-token (RIDER_INTERNAL_SERVICE_TOKEN) is not set; "
                        + "communication audit events stay queued until it is");
            }
            return 0;
        }
        try {
            return drainOnce(cfg);
        } catch (RuntimeException e) {
            // Scheduler boundary: log with context and wait for the next run.
            logger.warn("Audit outbox drain failed: {}", e.getMessage(), e);
            return 0;
        }
    }

    private int drainOnce(NmsProperties.Audit.Publish cfg) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<AuditOutboxEntry> leased = new ArrayList<>();
        for (AuditOutboxEntry entry : repository.findDue(now, cfg.getBatchSize())) {
            if (repository.tryLease(entry.getId(), instanceId, now, now.plusSeconds(cfg.getLeaseSeconds()))) {
                leased.add(entry);
            }
        }
        if (leased.isEmpty()) {
            return 0;
        }
        try {
            sink.send(leased.stream().map(AuditOutboxEntry::getPayload).toList());
        } catch (RuntimeException e) {
            reschedule(leased, cfg, now, e);
            return 0;
        }
        LocalDateTime sentAt = LocalDateTime.now(clock);
        leased.forEach(entry -> repository.markSent(entry.getId(), sentAt));
        return leased.size();
    }

    private void reschedule(List<AuditOutboxEntry> entries, NmsProperties.Audit.Publish cfg, LocalDateTime now,
            RuntimeException cause) {
        String reason = ErrorSanitizer.sanitize(cause.getMessage(), NotificationUseCaseConstants.AUDIT_OUTBOX_ERROR_MAX_LENGTH);
        logger.warn("Audit service did not take a batch of {} events (will retry): {}", entries.size(), reason);
        for (AuditOutboxEntry entry : entries) {
            int attempts = entry.getAttempts() + 1;
            boolean dead = attempts >= cfg.getMaxAttempts();
            repository.markFailed(entry.getId(), attempts, now.plusSeconds(backoffSeconds(cfg, attempts)), reason, dead);
            if (dead) {
                logger.error("Audit event {} gave up after {} attempts", entry.getEventId(), attempts);
            }
        }
    }

    /** Exponential backoff, doubling from the initial delay up to the configured maximum. */
    long backoffSeconds(NmsProperties.Audit.Publish cfg, int attempts) {
        long delay = cfg.getRetryInitialBackoffSeconds();
        for (int i = 1; i < attempts && delay < cfg.getRetryMaxBackoffSeconds(); i++) {
            delay *= 2;
        }
        return Math.min(delay, cfg.getRetryMaxBackoffSeconds());
    }
}
