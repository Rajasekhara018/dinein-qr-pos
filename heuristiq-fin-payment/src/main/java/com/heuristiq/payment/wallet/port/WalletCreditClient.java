package com.heuristiq.paymentservice.wallet.port;

/**
 * Outbound port to Rider_WalletService, the only owner of user balances and ledger entries. Payment uses it for one
 * thing: crediting a gateway-verified top-up.
 */
public interface WalletCreditClient {

    /**
     * Credits the user's wallet for a verified top-up. The call is idempotent on {@link WalletCreditCommand#topupId()}:
     * repeating it never credits twice.
     *
     * @return the id of the wallet journal that holds the credit, or null when the wallet did not report one
     * @throws WalletCreditRejectedException when the wallet answered with a refusal (for example 409
     *                                       PURCHASE_DISABLED); the top-up must stay awaiting credit
     * @throws com.heuristiq.resilient.DownstreamUnavailableException when the wallet could not be reached
     */
    String credit(WalletCreditCommand command);
}
