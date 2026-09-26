package com.heuristq.dinein.payment.gateway;

import java.util.List;
import java.util.Map;

/**
 * Provider SPI. Each payment gateway (Razorpay, PayU, Pine Labs, ...) implements this once; the rest of the system
 * only deals with provider-neutral types. To add a provider: implement this interface as a Spring bean, give it a
 * unique {@link #code()}, and select it with {@code app.payments.provider}.
 *
 * <p>Implementations must never trust the browser: {@link #verifyClientCallback} checks the provider's signature or
 * hash, and callers additionally confirm status/amount via {@link #fetchPayment} before marking an order paid.
 */
public interface PaymentGateway {

    /** Stable upper-case identifier stored on every payment row, e.g. {@code RAZORPAY}. */
    String code();

    /** True when credentials are present; unconfigured gateways cannot be selected as active. */
    boolean isConfigured();

    /**
     * Whether a failed attempt may be retried on the same provider order. Razorpay: yes (one order, many attempts).
     * PayU: no (a txnid can be used once), so a fresh provider order is created for each retry.
     */
    boolean reusableAfterFailure();

    /** Creates (or allocates) the provider-side order and returns its id. */
    String createProviderOrder(CheckoutContext context);

    /** Builds what the browser needs to start payment for an existing provider order. */
    CheckoutPayload checkoutPayload(CheckoutContext context);

    /**
     * Validates the parameters the browser (or a provider redirect) sends back after payment.
     *
     * @throws com.heuristq.dinein.shared.exception.ApiException 400 INVALID_SIGNATURE when tampered
     */
    ClientVerification verifyClientCallback(Map<String, String> params);

    /** Authoritative status of a single payment, read from the provider's API. */
    ProviderPayment fetchPayment(String providerOrderId, String providerPaymentId);

    /** All payment attempts for a provider order (used to reconcile before expiring unpaid orders). */
    List<ProviderPayment> fetchOrderPayments(String providerOrderId);

    /** Verifies the webhook signature against the raw body and normalises its events. */
    WebhookParseResult parseWebhook(byte[] rawBody, Map<String, String> headers);

    /** Starts a full or partial refund. */
    ProviderRefund refund(String providerOrderId, String providerPaymentId, long amountPaise, String reference);
}
