package com.heuristq.dinein.payment.gateway.pinelabs;

import com.heuristq.dinein.payment.gateway.CheckoutContext;
import com.heuristq.dinein.payment.gateway.CheckoutMode;
import com.heuristq.dinein.payment.gateway.CheckoutPayload;
import com.heuristq.dinein.payment.gateway.ClientVerification;
import com.heuristq.dinein.payment.gateway.PaymentGateway;
import com.heuristq.dinein.payment.gateway.ProviderPayment;
import com.heuristq.dinein.payment.gateway.ProviderRefund;
import com.heuristq.dinein.payment.gateway.WebhookParseResult;
import com.heuristq.dinein.shared.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Pine Labs (Plural) adapter skeleton. It is registered so the provider code is known, but reports itself as not
 * configured, so it cannot be selected until implemented. Implementation outline (see README "Adding a payment
 * provider"):
 * <ol>
 *   <li>{@link #createProviderOrder}: obtain an access token, create a hosted-checkout order, return its order id.</li>
 *   <li>{@link #checkoutPayload}: return {@link CheckoutMode#REDIRECT} with {@code {"url": <redirect_url>}}.</li>
 *   <li>{@link #verifyClientCallback}: verify the callback signature, return the order and payment ids.</li>
 *   <li>{@link #fetchPayment}/{@link #fetchOrderPayments}: read the order status API.</li>
 *   <li>{@link #parseWebhook}: verify the webhook signature over the raw body, map events.</li>
 *   <li>{@link #refund}: call the refund API.</li>
 * </ol>
 */
@Component
public class PineLabsPaymentGateway implements PaymentGateway {

    public static final String CODE = "PINELABS";

    @SuppressWarnings("unused")
    private final PineLabsProperties props;

    public PineLabsPaymentGateway(PineLabsProperties props) {
        this.props = props;
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public boolean isConfigured() {
        return false;
    }

    @Override
    public boolean reusableAfterFailure() {
        return false;
    }

    @Override
    public String createProviderOrder(CheckoutContext context) {
        throw notImplemented();
    }

    @Override
    public CheckoutPayload checkoutPayload(CheckoutContext context) {
        throw notImplemented();
    }

    @Override
    public ClientVerification verifyClientCallback(Map<String, String> params) {
        throw notImplemented();
    }

    @Override
    public ProviderPayment fetchPayment(String providerOrderId, String providerPaymentId) {
        throw notImplemented();
    }

    @Override
    public List<ProviderPayment> fetchOrderPayments(String providerOrderId) {
        throw notImplemented();
    }

    @Override
    public WebhookParseResult parseWebhook(byte[] rawBody, Map<String, String> headers) {
        return WebhookParseResult.invalid("pinelabs.unsupported", "{}");
    }

    @Override
    public ProviderRefund refund(String providerOrderId, String providerPaymentId, long amountPaise, String reference) {
        throw notImplemented();
    }

    private static ApiException notImplemented() {
        return new ApiException(HttpStatus.NOT_IMPLEMENTED, "PROVIDER_NOT_IMPLEMENTED",
                "Pine Labs integration is not implemented yet");
    }
}
