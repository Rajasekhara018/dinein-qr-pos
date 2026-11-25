package com.heuristiq.documents.centralaudit;

import java.time.Instant;
import java.util.List;

/** Persistence port of the audit outbox (Mongo and Postgres adapters). */
public interface AuditOutboxStore {

    /**
     * Stores a new entry. Returns {@code false} (and stores nothing) when an entry with the same {@code eventId}
     * already exists, so queueing the same event twice is harmless.
     */
    boolean insertIfAbsent(OutboxEntry entry);

    /** Pending entries that are due and not leased, oldest first, at most {@code limit}. */
    List<OutboxEntry> findDue(Instant now, int limit);

    /** Atomically leases one entry; true only for the caller that won it, so instances never send the same row. */
    boolean tryLease(String id, String owner, Instant now, Instant leaseUntil);

    void markSent(String id, Instant sentAt);

    /** Records a failed push and schedules the next try, or marks the entry dead. */
    void markFailed(String id, int attempts, Instant nextAttemptAt, String error, boolean dead);

    long countByStatus(OutboxStatus status);

    /** Deletes entries past their expiry. No-op where the database expires them natively (Mongo TTL). */
    void purgeExpired(Instant now);
}
