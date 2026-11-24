package com.heuristiq.paymentservice.payment.model;

/** Where one refund pays out. Exactly one destination per refund. */
public enum RefundDestination {
    /** Back to the original payment method through the gateway. Only for gateway payments. */
    GATEWAY,
    /** Into the customer's internal wallet. */
    WALLET
}
