package com.heuristq.dinein.payment.gateway;

/**
 * Result of a successfully verified client/redirect callback. {@code providerPaymentId} may be null on failure.
 * {@code internalOrderId} is set instead of {@code providerOrderId} when a provider's (unsigned) redirect only lets us
 * identify our own order; the caller then uses that order's latest payment with this provider.
 */
public record ClientVerification(String providerOrderId, String providerPaymentId, Long internalOrderId) {

    public ClientVerification(String providerOrderId, String providerPaymentId) {
        this(providerOrderId, providerPaymentId, null);
    }

    public static ClientVerification forInternalOrder(Long orderId) {
        return new ClientVerification(null, null, orderId);
    }
}
