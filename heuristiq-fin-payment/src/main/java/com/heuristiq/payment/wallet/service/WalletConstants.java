package com.heuristiq.paymentservice.wallet.service;

/** Vocabulary of the legacy {@code wallet_transactions} refund ledger, and of the top-up credit via the wallet. */
public final class WalletConstants {

    public static final String TYPE_CREDIT = "CREDIT";
    public static final String TYPE_DEBIT = "DEBIT";
    public static final String SOURCE_TOPUP = "TOPUP";
    public static final String SOURCE_REFUND = "REFUND";
    public static final String SOURCE_RIDE_PAYMENT = "RIDE_PAYMENT";
    public static final String STATUS_SUCCESS = "SUCCESS";

    public static final String KEY_TOPUP = "TOPUP:";
    public static final String KEY_REFUND = "REFUND:";
    public static final String KEY_RIDE_PAYMENT = "RIDE_PAYMENT:";

    /** Top-up credit via Rider_WalletService. */
    public static final int MAX_ERROR_LENGTH = 500;
    public static final int MAX_BACKOFF_DOUBLINGS = 20;
    public static final String CORRELATION_KEY = "correlationId";
    public static final String CORRELATION_PREFIX = "topup-credit-";
    public static final String REASON_WALLET_UNAVAILABLE = "WALLET_UNAVAILABLE";
    public static final String REASON_MISSING_JOURNAL = "WALLET_JOURNAL_MISSING";
    public static final String REASON_UNEXPECTED = "UNEXPECTED_";

    private WalletConstants() {
    }

    /** Decimal places of every wallet amount (rupees and paise). */
    public static final int MONEY_SCALE = 2;
}
