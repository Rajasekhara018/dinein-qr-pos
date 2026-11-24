package com.heuristiq.paymentservice.wallet.client;

import com.heuristiq.paymentservice.shared.money.Money;
/** Body of {@code POST /internal/refund-credits} on Rider_WalletService; the amount travels as {@code {code, value}}. */
record WalletRefundCreditRequest(String userId, String appCode, Money amount, String paymentId, String refundId) {
}
