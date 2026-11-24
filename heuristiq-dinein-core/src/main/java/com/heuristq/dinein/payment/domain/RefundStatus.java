package com.heuristq.dinein.payment.domain;

public enum RefundStatus {
    PENDING,
    PROCESSED,
    FAILED,
    /** Offline (counter) payment: nothing is sent to a gateway; the money is handed back by staff. */
    MANUAL
}
