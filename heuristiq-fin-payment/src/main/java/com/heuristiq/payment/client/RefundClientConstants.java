package com.heuristiq.payment.client;

import java.time.Duration;

/** Constants of the outbound refund calls. */
public final class RefundClientConstants {

    /** Upper bound for a blocking refund call to a gateway. */
    public static final Duration TIMEOUT = Duration.ofSeconds(30);

    /** Razorpay's idempotency header for refund creation. */
    public static final String IDEMPOTENCY_HEADER = "X-Refund-Idempotency";

    private RefundClientConstants() {
    }
}
