package com.heuristiq.payment.wallet.client;

import com.heuristiq.payment.shared.money.Money;
/** Body of {@code POST /internal/purchases} on Rider_WalletService; the amount travels as {@code {code, value}}. */
record WalletPurchaseRequest(String userId, String appCode, Money amount, String paymentId) {
}
