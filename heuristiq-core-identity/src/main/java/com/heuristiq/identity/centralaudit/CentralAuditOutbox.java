package com.heuristiq.identity.centralaudit;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Port: durable queue of audit events waiting for the central audit service. An event is added in its own
 * transaction (never the caller's, so a failed insert cannot abort a business transaction), a drainer claims due rows under a short lease (so two UAM instances never send the same
 * row), and a row is removed only after delivery.
 */
public interface CentralAuditOutbox {

    /** A claimed row: its event id, the organization it belongs to, the serialised event and the failed attempts so far. */
    record Entry(String eventId, String tenantId, String payload, int attempts) {
    }

    /** Stores the event; a repeated {@code eventId} is ignored. */
    void add(String eventId, String tenantId, String payload, Instant now);

    /** Claims up to {@code max} rows that are due and not leased by another drainer, oldest first, leasing them for {@code lease}. */
    List<Entry> claim(int max, Instant now, Duration lease);

    /** Removes delivered rows. */
    void delete(List<String> eventIds);

    /** Releases the lease of a failed row and makes it due again at {@code nextAttemptAt}. */
    void reschedule(String eventId, int attempts, Instant nextAttemptAt);

    /** Number of rows held. */
    long count();

    /**
     * Deletes up to {@code max} of the oldest rows created before {@code olderThan} (never newer rows).
     *
     * @return rows deleted
     */
    int deleteOldest(Instant olderThan, int max);
}
