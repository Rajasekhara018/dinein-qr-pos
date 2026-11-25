package com.heuristiq.payment.wallet.port;

import com.heuristiq.payment.shared.money.Money;

/**
 * One top-up credit to post in the wallet.
 *
 * @param topupId  the top-up session id; the idempotency key and the wallet's {@code paymentId}
 * @param userId   the customer whose wallet is credited
 * @param tenantId the organization of the top-up, or null for a top-up recorded before organizations
 * @param amount   the verified paid amount, with its currency
 */
public record WalletCreditCommand(String topupId, String userId, String tenantId, Money amount, String appCode) {
    public WalletCreditCommand(String topupId, String userId, String tenantId, Money amount) {
        this(topupId, userId, tenantId, amount, null);
    }
}
