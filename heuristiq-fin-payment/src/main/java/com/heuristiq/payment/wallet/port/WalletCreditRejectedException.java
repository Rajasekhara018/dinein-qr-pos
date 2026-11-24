package com.heuristiq.paymentservice.wallet.port;

/**
 * The wallet answered a credit (a top-up or a refund credit) with a refusal (HTTP 4xx). Carries the wallet's contract
 * error code when it sent one.
 */
public class WalletCreditRejectedException extends RuntimeException {

    private final int httpStatus;
    private final String errorCode;

    public WalletCreditRejectedException(int httpStatus, String errorCode) {
        super("Wallet refused the credit: HTTP " + httpStatus + (errorCode == null ? "" : " " + errorCode));
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
    }

    public int httpStatus() {
        return httpStatus;
    }

    /** The wallet's contract error code (for example PURCHASE_DISABLED), or null. */
    public String errorCode() {
        return errorCode;
    }
}
