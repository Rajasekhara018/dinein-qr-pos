package com.heuristiq.payment.wallet.port;

import com.heuristiq.payment.shared.money.Money;

/** Outbound port: asks Rider_WalletService, the only owner of balances, what a user holds. Read-only. */
public interface WalletBalanceClient {

    /**
     * @param userId   the wallet owner
     * @param tenantId the organization to read in, or null to leave it to the propagated session organization
     * @return the spendable balance as the wallet reports it, with its currency; never a guess
     * @throws com.heuristiq.resilient.DownstreamUnavailableException when the wallet could not give a usable answer
     *                                                              (unreachable, refused, or an unreadable reply)
     */
    Money balanceOf(String userId, String tenantId);
}
