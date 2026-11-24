package com.heuristiq.paymentservice.events;

/** Fixed values of the durable event outbox: its SQL, its retry backoff and the Kafka client limits it relies on. */
final class OutboxConstants {

    private OutboxConstants() {
    }

    /** Longest wait in seconds before a failed row is tried again. */
    static final long MAX_RETRY_DELAY_SECONDS = 300;
    /** The retry delay stops doubling after this many attempts. */
    static final int MAX_RETRY_DELAY_DOUBLINGS = 8;

    /** Kafka producer settings that bound how long one send can stay unsettled, and the client defaults. */
    static final String PRODUCER_MAX_BLOCK_MS = "max.block.ms";
    static final String PRODUCER_DELIVERY_TIMEOUT_MS = "delivery.timeout.ms";
    static final long KAFKA_DEFAULT_MAX_BLOCK_MS = 60_000;
    static final long KAFKA_DEFAULT_DELIVERY_TIMEOUT_MS = 120_000;

    static final String ENQUEUE =
            "INSERT INTO integration_outbox(event_id, topic, aggregate_key, payload) VALUES (?, ?, ?, ?)";

    /**
     * Claims the oldest due rows by pushing {@code next_attempt_at} past the lease, in one short statement, so nothing
     * stays locked during the Kafka send and another instance skips the rows. The new {@code next_attempt_at} is
     * returned and serves as this run's claim token. The NOT EXISTS rule keeps per-aggregate ordering: only the
     * first unpublished row of a key can be claimed, so a batch holds at most one row per key, and a claimed or failed
     * row holds back every later row of its key, on every instance, until it is published.
     * The CTE keeps the LIMIT and {@code SKIP LOCKED} evaluated exactly once.
     * Parameters: batch size, lease in milliseconds.
     */
    static final String CLAIM_BATCH = """
            WITH due AS (
                SELECT o.id FROM integration_outbox o
                WHERE o.published_at IS NULL AND o.next_attempt_at <= CURRENT_TIMESTAMP
                AND NOT EXISTS (SELECT 1 FROM integration_outbox p
                    WHERE p.aggregate_key = o.aggregate_key AND p.id < o.id AND p.published_at IS NULL)
                ORDER BY o.id LIMIT ? FOR UPDATE OF o SKIP LOCKED
            )
            UPDATE integration_outbox t SET next_attempt_at = CURRENT_TIMESTAMP + (? * INTERVAL '1 millisecond')
            FROM due WHERE t.id = due.id
            RETURNING t.id, t.event_id, t.topic, t.aggregate_key, t.payload, t.attempts, t.next_attempt_at
            """;

    /** Unconditional: a delivered event is published whoever holds the row now. */
    static final String MARK_PUBLISHED = "UPDATE integration_outbox SET published_at = CURRENT_TIMESTAMP WHERE id = ?";

    /** Only while this run still holds the lease (its claim token), so a late run never undoes another's claim. */
    static final String RECORD_FAILURE = "UPDATE integration_outbox SET attempts = ?, "
            + "next_attempt_at = CURRENT_TIMESTAMP + (? * INTERVAL '1 second') "
            + "WHERE id = ? AND next_attempt_at = ? AND published_at IS NULL";

    /** Gives a claimed row that was never handed to Kafka straight back, without counting an attempt; lease-guarded. */
    static final String RELEASE_UNSENT = "UPDATE integration_outbox SET next_attempt_at = CURRENT_TIMESTAMP "
            + "WHERE id = ? AND next_attempt_at = ? AND published_at IS NULL";
}
