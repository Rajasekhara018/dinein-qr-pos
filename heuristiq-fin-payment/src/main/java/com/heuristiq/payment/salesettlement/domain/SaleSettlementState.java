package com.heuristiq.payment.salesettlement.domain;

/** Where a settle or cancel request stands. */
public enum SaleSettlementState {
    /** Waiting to be sent, or to be sent again after its backoff. */
    PENDING,
    /** The wallet answered for good (settled, cancelled, nothing to cancel, or already done). */
    DONE,
    /** Not sent: a cancellation whose sale was paid after all (another capture of the same sale). */
    SUPERSEDED,
    /** Given up: the wallet refused the request as such, no organization is known, or it ran out of attempts. */
    FAILED
}
