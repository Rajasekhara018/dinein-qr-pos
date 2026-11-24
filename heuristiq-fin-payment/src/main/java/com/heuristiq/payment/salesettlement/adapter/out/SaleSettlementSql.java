package com.heuristiq.paymentservice.salesettlement.adapter.out;

/** SQL of the sale settlement request store ({@code sale_settlement_requests}, V26). */
final class SaleSettlementSql {

    private SaleSettlementSql() {
    }

    /** Parameters: idempotency key, event id, action, payment id, tenant id, reason. A repeat of the key is ignored. */
    static final String ENQUEUE = "INSERT INTO sale_settlement_requests (idempotency_key, event_id, action, payment_id, tenant_id, reason) "
            + "VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (idempotency_key) DO NOTHING";

    /**
     * Due PENDING rows, oldest first, and only the first pending row of each sale (so a cancellation queued before a later capture
     * is decided before the settlement). Parameters: now, limit.
     */
    static final String DUE = "SELECT r.id, r.event_id, r.action, r.idempotency_key, r.payment_id, r.tenant_id, r.reason, r.attempts, "
            + "r.created_at "
            + "FROM sale_settlement_requests r WHERE r.status = 'PENDING' AND r.next_attempt_at <= ? "
            + "AND NOT EXISTS (SELECT 1 FROM sale_settlement_requests p WHERE p.event_id = r.event_id AND p.status = 'PENDING' "
            + "AND p.id < r.id) ORDER BY r.next_attempt_at, r.id LIMIT ?";

    /** Parameters: status, outcome, wallet journal id, last error, completed at, id. */
    static final String FINISH = "UPDATE sale_settlement_requests SET status = ?, outcome = ?, wallet_journal_id = ?, last_error = ?, "
            + "completed_at = ? WHERE id = ? AND status = 'PENDING'";

    /** Parameters: attempts, last error, next attempt at, id. */
    static final String RETRY_LATER = "UPDATE sale_settlement_requests SET attempts = ?, last_error = ?, next_attempt_at = ? "
            + "WHERE id = ? AND status = 'PENDING'";

    /** The sale is not booked yet: wait without counting an attempt. Parameters: last error, next attempt at, id. */
    static final String AWAIT_SALE = "UPDATE sale_settlement_requests SET last_error = ?, next_attempt_at = ? "
            + "WHERE id = ? AND status = 'PENDING'";

    /**
     * Whether the payment's sale was paid after all: the payment, or another one for the same ride (ride payments) or the same
     * source application and reference, is in a status reached only once money arrived. Parameter: payment id.
     */
    static final String SALE_PAID_AFTER_ALL = "SELECT COUNT(*) FROM payments p JOIN payments q ON "
            + "((p.source_service IS NULL AND q.source_service IS NULL AND p.ride_id = q.ride_id) "
            + "OR (p.source_service = q.source_service AND p.source_reference = q.source_reference) "
            + "OR p.internal_payment_id = q.internal_payment_id) "
            + "WHERE p.internal_payment_id = ? "
            + "AND q.status IN ('CAPTURED', 'SETTLED', 'PARTIALLY_REFUNDED', 'REFUNDED', 'REVERSED', 'AMOUNT_MISMATCH')";
}
