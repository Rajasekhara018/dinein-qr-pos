package com.heuristiq.payment.wallet.port;

/**
 * Outbound port to Rider_WalletService, the only owner of user balances: credits a refund whose destination is the
 * wallet.
 */
public interface WalletRefundCreditClient {

    /**
     * Credits the user's wallet for a refund. Idempotent on {@link WalletRefundCreditCommand#refundId()}: repeating it
     * never credits twice.
     *
     * @return the id of the wallet journal that holds the credit, or null when the wallet did not report one
     * @throws WalletCreditRejectedException when the wallet answered with a refusal (HTTP 4xx, for example 409
     *                                       APP_NOT_ENABLED); nothing was credited
     * @throws com.heuristiq.resilient.DownstreamUnavailableException when the wallet could not be reached; the credit may
     *                                                              or may not have been booked, so repeat with the same id
     */
    String credit(WalletRefundCreditCommand command);
}
