package com.heuristq.dinein.payment.domain;

/** How a counter / waiter payment was taken. Stored in {@code payment.method} for payments of provider OFFLINE. */
public enum OfflinePaymentMethod {
    CASH,
    UPI_AT_COUNTER,
    CARD_AT_COUNTER
}
