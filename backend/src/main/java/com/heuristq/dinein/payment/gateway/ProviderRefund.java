package com.heuristq.dinein.payment.gateway;

/** @param processed true when the provider reports the refund as already completed */
public record ProviderRefund(String refundId, boolean processed) {
}
