package com.heuristq.dinein.payment.gateway;

/** Result of a successfully verified client/redirect callback. {@code providerPaymentId} may be null on failure. */
public record ClientVerification(String providerOrderId, String providerPaymentId) {
}
