package com.heuristq.dinein.payment.infrastructure.gateway;

/** A payment attempt as reported by a provider, normalised. */
public record ProviderPayment(String providerOrderId, String providerPaymentId, Outcome outcome, long amountPaise,
                              String method, String errorDescription) {

    public enum Outcome { CAPTURED, AUTHORIZED, FAILED, PENDING }

    public boolean isCaptured() {
        return outcome == Outcome.CAPTURED;
    }
}
