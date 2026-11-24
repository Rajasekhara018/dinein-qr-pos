package com.heuristiq.payment.refundaccounting.domain;

public final class RefundAccountingConstants {
    public static final long INITIAL_RETRY_SECONDS = 30;
    public static final long MAX_RETRY_SECONDS = 3600;
    public static final int MAX_BACKOFF_SHIFT = 7;
    public static final long LEASE_SECONDS = 120;
    public static final int MAX_ERROR_LENGTH = 500;
    public static final String POST_PATH = "/api/v1/franchise/internal/ledger/payment-refunds";
    public static final String POSTED = "SALE_REFUNDED";
    private RefundAccountingConstants() { }
}
