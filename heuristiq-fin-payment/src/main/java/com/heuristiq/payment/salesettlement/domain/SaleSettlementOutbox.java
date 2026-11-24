package com.heuristiq.paymentservice.salesettlement.domain;

import java.time.Instant;
import java.util.List;

/** Port: the durable store of settle and cancel requests (table {@code sale_settlement_requests}). */
public interface SaleSettlementOutbox {

    /**
     * Stores a request in the caller's transaction; a second request with the same idempotency key is ignored.
     *
     * @return true when stored, false when one with the same key was already there
     */
    boolean enqueue(String eventId, SaleSettlementAction action, String idempotencyKey, String paymentId, String tenantId,
                    String reason);

    /**
     * The oldest due PENDING requests, at most {@code limit}, and for each sale only its first pending request, so a sale's
     * requests are sent in the order they were made.
     */
    List<SaleSettlementRequest> due(Instant now, int limit);

    /** Finishes a request: DONE, SUPERSEDED or FAILED, with what the wallet answered. */
    void finish(long id, SaleSettlementState state, String outcome, String walletJournalId, String lastError, Instant at);

    /** Records a failed send: one more attempt, the error, and when to try again. */
    void retryLater(long id, int attempts, String lastError, Instant nextAttemptAt);

    /**
     * @return true when the money of the payment's sale arrived after all: the payment itself, or another payment of the same
     *         ride or source reference, reached CAPTURED or a later status (a cancellation must then not be sent)
     */
    boolean salePaidAfterAll(String paymentId);

    /**
     * The request's sale is not booked in the wallet yet: it stays PENDING and is looked at again at {@code nextCheckAt}. Its
     * attempts are not counted (waiting for the sale is not a failure).
     */
    void awaitSale(long id, String lastError, Instant nextCheckAt);
}
