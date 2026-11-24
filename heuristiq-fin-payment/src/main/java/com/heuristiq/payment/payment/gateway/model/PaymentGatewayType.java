package com.heuristiq.paymentservice.payment.gateway.model;

import com.heuristiq.paymentservice.shared.exception.PaymentServiceException;

import java.util.Locale;

/**
 * The set of payment gateways this service can route an online payment to. Scope for this pass
 * (per the multi-gateway task): RAZORPAY, PAYU, TOUCAN, PINELABS. PayPal/Cashfree exist in the
 * reference project this design was ported from but were explicitly out of scope here.
 *
 * CASH is intentionally NOT a member of this enum — CASH is a {@link com.heuristiq.paymentservice.payment.model.PaymentMethod},
 * not a gateway; no gateway is ever involved in a cash payment. See Payment.gateway's javadoc for
 * the full method-vs-gateway relationship.
 */
public enum PaymentGatewayType {
    RAZORPAY("razorpay"),
    PAYU("payu"),
    TOUCAN("toucan"),
    PINELABS("pinelabs");

    private final String alias;

    PaymentGatewayType(String alias) {
        this.alias = alias;
    }

    /**
     * Case-insensitive lookup by enum name or lowercase alias (e.g. "payu", "PAYU", "PayU" all
     * resolve to PAYU). Mirrors the reference project's PaymentGatewayType.fromValue, minus the
     * PAYPAL/CASHFREE aliases this service doesn't support.
     */
    public static PaymentGatewayType fromValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new PaymentServiceException("gateway is required");
        }
        String trimmed = value.trim();
        String normalized = trimmed.toUpperCase(Locale.ROOT);
        for (PaymentGatewayType type : values()) {
            if (type.name().equals(normalized) || type.alias.equalsIgnoreCase(trimmed)) {
                return type;
            }
        }
        throw new PaymentServiceException("Unsupported payment gateway: " + value);
    }
}
