package com.heuristiq.payment.gatewayaudit.model;

/** What this service did with one received webhook delivery. */
public enum WebhookOutcome {
    /** Verified and applied (or deliberately a no-op transition) and recorded in the dedupe ledger. */
    PROCESSED,
    /** Verified but not actionable (no payment entity, unknown order, conflicting provider payment id); recorded only. */
    IGNORED,
    /** Verified, but the provider event id was already processed. */
    DUPLICATE,
    /** Refused: the signature did not verify. */
    REJECTED
}
