package com.heuristiq.paymentservice.wallet.port;

import com.heuristiq.paymentservice.shared.money.Money;

/**
 * One refund to credit to a customer's wallet.
 *
 * @param refundId  the refund row id; the idempotency key and the wallet's {@code refundId}
 * @param paymentId the refunded payment
 * @param userId    the customer whose wallet is credited
 * @param tenantId  the organization of the payment, or null for a payment recorded before organizations
 * @param amount    the refund amount, with its currency
 */
public record WalletRefundCreditCommand(String refundId, String paymentId, String userId, String tenantId,
                                        Money amount) {
}
