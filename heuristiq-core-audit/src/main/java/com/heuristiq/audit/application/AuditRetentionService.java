package com.heuristiq.audit.application;

import static com.heuristiq.audit.application.RetentionConstants.LOCK_NAME;
import static com.heuristiq.audit.application.RetentionConstants.MIN_LEASE_MS;

import com.heuristiq.audit.config.AuditProperties;
import com.heuristiq.audit.domain.AuditEventRepository;
import com.heuristiq.audit.domain.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;

/**
 * Deletes events (and their payloads) whose retention has run out. Runs under a {@link SchedulerLock}, so only one
 * instance purges at a time. MongoDB also removes expired documents through its TTL indexes; this is what does it
 * on PostgreSQL and a safety net on MongoDB.
 */
@Service
public class AuditRetentionService {

    private static final Logger log = LoggerFactory.getLogger(AuditRetentionService.class);

    private final AuditEventRepository repository;
    private final SchedulerLock lock;
    private final Clock clock;
    private final long leaseMs;

    /**
     * Creates the service.
     *
     * @param repository the audit store
     * @param lock       cross-instance lease
     * @param clock      time source
     * @param properties service settings (the lease is half the run interval, at least a minute)
     */
    public AuditRetentionService(AuditEventRepository repository, SchedulerLock lock, Clock clock,
                                 AuditProperties properties) {
        this.repository = repository;
        this.lock = lock;
        this.clock = clock;
        this.leaseMs = Math.max(MIN_LEASE_MS, properties.getRetention().getPurgeIntervalMs() / 2);
    }

    /**
     * One purge round.
     *
     * @return the number of events removed (0 when another instance holds the lease or the purge failed)
     */
    public long purge() {
        if (!lock.tryAcquire(LOCK_NAME, leaseMs)) {
            return 0;
        }
        try {
            long removed = repository.deleteExpired(clock.instant());
            if (removed > 0) {
                log.info("audit.retention.purged count={}", removed);
            }
            return removed;
        } catch (RuntimeException e) {
            log.warn("audit.retention.failed reason={}", e.getClass().getSimpleName());
            return 0;
        } finally {
            lock.release(LOCK_NAME);
        }
    }
}
