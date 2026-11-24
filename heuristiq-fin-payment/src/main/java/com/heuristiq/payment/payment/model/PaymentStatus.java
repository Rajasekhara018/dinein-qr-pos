package com.heuristiq.paymentservice.payment.model;

/** Payment state machine per HLD §17.2; legal moves are defined in {@link PaymentStateMachine}. */
public enum PaymentStatus {
    INITIATED,
    AUTHORIZED,
    CAPTURED,
    SETTLED,
    FAILED,
    CANCELLED,
    EXPIRED,
    REVERSED,
    REFUNDED,
    PARTIALLY_REFUNDED,
    /** The gateway reported a paid amount/currency different from the stored payment; needs manual review. */
    AMOUNT_MISMATCH
}
