package com.heuristq.dinein.order.domain;

import java.util.EnumSet;
import java.util.Set;

public enum OrderStatus {
    PENDING_PAYMENT,
    CONFIRMED,
    PREPARING,
    READY,
    COMPLETED,
    EXPIRED,
    PAYMENT_FAILED,
    CANCELLED;

    /** Paid statuses that are visible to the kitchen. */
    public static final Set<OrderStatus> KITCHEN_VISIBLE = EnumSet.of(CONFIRMED, PREPARING, READY);

    /** Statuses that count as revenue in reports. */
    public static final Set<OrderStatus> PAID = EnumSet.of(CONFIRMED, PREPARING, READY, COMPLETED);

    /** Orders that will never change again, so their guest PII is safe to redact after a retention period. */
    public static final Set<OrderStatus> TERMINAL = EnumSet.of(COMPLETED, EXPIRED, PAYMENT_FAILED, CANCELLED);
}
