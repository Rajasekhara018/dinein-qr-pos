package com.heuristiq.notification.domain.repository;

import java.time.LocalDateTime;
import java.util.List;

import com.heuristiq.notification.domain.model.AuditOutboxEntry;
import com.heuristiq.notification.domain.model.AuditOutboxStatus;

/** Port for the audit outbox. */
public interface IAuditOutboxRepository {

    /**
     * Stores a new entry. Returns {@code false} (and stores nothing) when an entry with the same
     * {@code eventId} already exists, so enqueueing the same event twice is harmless.
     */
    boolean insertIfAbsent(AuditOutboxEntry entry);

    /** Pending entries that are due and not leased, oldest first, at most {@code limit}. */
    List<AuditOutboxEntry> findDue(LocalDateTime now, int limit);

    /**
     * Atomically leases one entry to {@code owner} until {@code leaseUntil}. Returns {@code true}
     * only for the caller that won the lease, so several NMS instances never send the same row.
     */
    boolean tryLease(String id, String owner, LocalDateTime now, LocalDateTime leaseUntil);

    void markSent(String id, LocalDateTime sentAt);

    /** Records a failed delivery and schedules the next try, or marks the entry dead when {@code dead}. */
    void markFailed(String id, int attempts, LocalDateTime nextAttemptAt, String error, boolean dead);

    long countByStatus(AuditOutboxStatus status);

    /** Deletes entries past their expiry. No-op on adapters that expire natively (Mongo TTL). */
    void purgeExpired(LocalDateTime now);
}
