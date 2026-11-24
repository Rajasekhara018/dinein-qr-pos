package com.heuristiq.paymentservice.refundaccounting.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Accounting delivery for an already verified customer refund; it never pays the customer again. */
public interface RefundAccounting {
    record Posting(String refundEventId, String tenantId, String eventId, BigDecimal amount, String currency,
                   boolean creditAlreadyPosted, int attempts) { }
    interface Store {
        void enqueue(Posting posting);
        List<Posting> claim(Instant now);
        void delivered(Posting posting);
        void retry(Posting posting, Instant nextAttempt, String reason);
    }
    interface Gateway { void post(Posting posting); }
}
