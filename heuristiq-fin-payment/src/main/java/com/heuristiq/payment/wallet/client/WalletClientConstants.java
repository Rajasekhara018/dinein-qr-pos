package com.heuristiq.paymentservice.wallet.client;

/** Fixed wire values of the Payment -> Wallet credit call. */
public final class WalletClientConstants {

    /** Resilient-caller name of Rider_WalletService. */
    public static final String WALLET_SERVICE_NAME = "wallet-service";
    /** Path of the verified-purchase endpoint, relative to the wallet internal base URL. */
    public static final String PURCHASES_PATH = "/purchases";
    /** Path of a user's balance read, relative to the wallet internal base URL; the user id is the one variable. */
    public static final String USER_BALANCE_PATH = "/users/{userId}/balance";
    public static final String BALANCE_FIELD = "balance";
    /** Path of the refund-credit endpoint, relative to the wallet internal base URL. */
    public static final String REFUND_CREDITS_PATH = "/refund-credits";
    /** Prefix of the refund-credit idempotency key; the wallet keeps one journal per key. */
    public static final String REFUND_KEY_PREFIX = "REFUND:";
    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    public static final String SERVICE_NAME_HEADER = "X-Rider-Service-Name";
    public static final String SERVICE_NAME = "rider-payment-service";
    /** Prefix of the idempotency key; the wallet keeps one journal per key. */
    public static final String IDEMPOTENCY_KEY_PREFIX = "PURCHASE:";
    /** Wallet error code of a 400 that refuses the request itself (for example a non-positive amount). */
    public static final String CODE_VALIDATION_ERROR = "VALIDATION_ERROR";
    /**
     * Wallet error codes of a 409 that are a definite business refusal: nothing was booked and booking the same
     * request later would not succeed without someone changing the app or the account. Every other outcome is unknown.
     */
    public static final java.util.Set<String> DEFINITE_REFUSAL_CODES_409 = java.util.Set.of(
            "APP_NOT_ENABLED", "PURCHASE_DISABLED", "ACCOUNT_FROZEN");
    public static final String ERROR_CODE_FIELD = "errorCode";
    public static final String RESPONSE_OBJECT_FIELD = "responseObject";
    public static final String JOURNAL_ID_FIELD = "journalId";
    /** Fields of an amount ({code, value}) in the wallet's replies. */
    public static final String MONEY_CODE_FIELD = "code";
    public static final String MONEY_VALUE_FIELD = "value";

    private WalletClientConstants() {
    }
}
