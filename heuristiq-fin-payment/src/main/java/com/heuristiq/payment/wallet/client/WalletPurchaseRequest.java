package com.heuristiq.paymentservice.wallet.client;

import com.heuristiq.paymentservice.shared.money.Money;
/** Body of {@code POST /internal/purchases} on Rider_WalletService; the amount travels as {@code {code, value}}. */
record WalletPurchaseRequest(String userId, String appCode, Money amount, String paymentId) {
}
