package com.heuristq.dinein.payment.infrastructure.gateway;

/** How the browser starts a payment. */
public enum CheckoutMode {
    /** Load the provider's JS SDK and open its modal (Razorpay Checkout). */
    SDK,
    /** Auto-submit an HTML form to the provider's hosted page (PayU). */
    FORM_POST,
    /** Navigate to a provider-hosted URL (Pine Labs and most redirect gateways). */
    REDIRECT
}
