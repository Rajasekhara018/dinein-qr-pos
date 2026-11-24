package com.heuristiq.paymentservice.payment.model;

/** Lifecycle of one {@link PaymentRefund}. PROCESSING and COMPLETED both count against the refundable amount. */
public enum RefundStatus {
    /** Marker saved; the gateway call (or wallet credit) is not yet recorded. */
    PROCESSING,
    COMPLETED,
    /** The gateway definitively rejected the refund; the amount is released. */
    FAILED
}
