package com.heuristiq.payment.wallet.model;

/**
 * Life of a top-up. CAPTURED means the gateway confirmed the payment AND Rider_WalletService holds the credit; it is
 * the status the Customer app waits for.
 */
public enum WalletTopupStatus {
    INITIATED,
    /**
     * The gateway confirmed the payment but the wallet has not (yet) booked the credit, for example because the wallet
     * was down or refused (409 PURCHASE_DISABLED). The money is never lost: the credit job retries it.
     */
    PAID_AWAITING_CREDIT,
    CAPTURED,
    FAILED,
    EXPIRED,
    /** The gateway reported a paid amount/currency different from the session; nothing was credited. */
    AMOUNT_MISMATCH
}
