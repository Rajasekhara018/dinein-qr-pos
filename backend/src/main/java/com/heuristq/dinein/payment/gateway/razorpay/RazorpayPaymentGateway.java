package com.heuristq.dinein.payment.gateway.razorpay;

import com.heuristq.dinein.payment.gateway.CheckoutContext;
import com.heuristq.dinein.payment.gateway.CheckoutMode;
import com.heuristq.dinein.payment.gateway.CheckoutPayload;
import com.heuristq.dinein.payment.gateway.ClientVerification;
import com.heuristq.dinein.payment.gateway.PaymentGateway;
import com.heuristq.dinein.payment.gateway.ProviderPayment;
import com.heuristq.dinein.payment.gateway.ProviderRefund;
import com.heuristq.dinein.payment.gateway.WebhookParseResult;
import com.heuristq.dinein.payment.gateway.WebhookParseResult.GatewayEvent;
import com.heuristq.dinein.shared.exception.ApiException;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Refund;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Razorpay: Orders API + Checkout.js ({@link CheckoutMode#SDK}), auto-capture, HMAC-SHA256 signatures and webhooks
 * ({@code payment.captured}, {@code order.paid}, {@code payment.failed}, {@code refund.processed}).
 */
@Slf4j
@Component
public class RazorpayPaymentGateway implements PaymentGateway {

    public static final String CODE = "RAZORPAY";

    private final RazorpayProperties props;
    private volatile RazorpayClient client;

    public RazorpayPaymentGateway(RazorpayProperties props) {
        this.props = props;
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public boolean isConfigured() {
        return props.isConfigured();
    }

    @Override
    public boolean reusableAfterFailure() {
        return true;
    }

    @Override
    public String createProviderOrder(CheckoutContext ctx) {
        JSONObject request = new JSONObject();
        request.put("amount", ctx.amountPaise());
        request.put("currency", ctx.currency());
        request.put("receipt", ctx.orderNumber());
        request.put("payment_capture", 1);
        request.put("notes", new JSONObject(Map.of("orderId", String.valueOf(ctx.orderId()),
                "orderNumber", ctx.orderNumber())));
        try {
            Order order = client().orders.create(request);
            String id = order.get("id");
            log.info("razorpay.order_created razorpayOrderId={} receipt={} amountPaise={}", id, ctx.orderNumber(), ctx.amountPaise());
            return id;
        } catch (RazorpayException e) {
            throw providerError("create order", e);
        }
    }

    @Override
    public CheckoutPayload checkoutPayload(CheckoutContext ctx) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("key", props.keyId());
        data.put("order_id", ctx.providerOrderId());
        data.put("amount", ctx.amountPaise());
        data.put("currency", ctx.currency());
        data.put("name", ctx.restaurantName());
        data.put("description", "Order " + ctx.orderNumber());
        Map<String, String> prefill = new LinkedHashMap<>();
        if (ctx.customerName() != null) {
            prefill.put("name", ctx.customerName());
        }
        if (ctx.customerPhone() != null) {
            prefill.put("contact", "+91" + ctx.customerPhone());
        }
        data.put("prefill", prefill);
        data.put("theme", Map.of("color", ctx.brandColor()));
        data.put("scriptUrl", "https://checkout.razorpay.com/v1/checkout.js");
        return new CheckoutPayload(CheckoutMode.SDK, data);
    }

    @Override
    public ClientVerification verifyClientCallback(Map<String, String> params) {
        String orderId = params.get("razorpay_order_id");
        String paymentId = params.get("razorpay_payment_id");
        String signature = params.get("razorpay_signature");
        if (!RazorpaySignatures.verifyPayment(orderId, paymentId, signature, props.keySecret())) {
            log.warn("razorpay.verify.invalid_signature razorpayOrderId={}", orderId);
            throw ApiException.badRequest("INVALID_SIGNATURE", "Payment could not be verified");
        }
        return new ClientVerification(orderId, paymentId);
    }

    @Override
    public ProviderPayment fetchPayment(String providerOrderId, String providerPaymentId) {
        try {
            return toPayment(client().payments.fetch(providerPaymentId).toJson());
        } catch (RazorpayException e) {
            throw providerError("fetch payment", e);
        }
    }

    @Override
    public List<ProviderPayment> fetchOrderPayments(String providerOrderId) {
        try {
            return client().orders.fetchPayments(providerOrderId).stream()
                    .map(p -> toPayment(p.toJson())).toList();
        } catch (RazorpayException e) {
            throw providerError("fetch order payments", e);
        }
    }

    @Override
    public WebhookParseResult parseWebhook(byte[] rawBody, Map<String, String> headers) {
        String body = new String(rawBody, StandardCharsets.UTF_8);
        JSONObject json;
        try {
            json = new JSONObject(body);
        } catch (JSONException e) {
            json = null;
        }
        String eventType = json == null ? null : json.optString("event", null);
        String payloadJson = json == null ? JSONObject.quote(body) : body;
        if (!RazorpaySignatures.verifyWebhook(rawBody, headers.get("x-razorpay-signature"), props.webhookSecret())) {
            return WebhookParseResult.invalid(eventType, payloadJson);
        }
        List<GatewayEvent> events = new ArrayList<>();
        JSONObject payload = json == null ? new JSONObject() : json.optJSONObject("payload", new JSONObject());
        JSONObject payment = entity(payload, "payment");
        JSONObject refund = entity(payload, "refund");
        switch (eventType == null ? "" : eventType) {
            case "payment.captured", "order.paid" -> {
                if (payment != null) {
                    events.add(new GatewayEvent(GatewayEvent.Type.PAYMENT_CAPTURED, toPayment(payment), null, null));
                }
            }
            case "payment.authorized" -> {
                if (payment != null) {
                    events.add(new GatewayEvent(GatewayEvent.Type.PAYMENT_AUTHORIZED, toPayment(payment), null, null));
                }
            }
            case "payment.failed" -> {
                if (payment != null) {
                    events.add(new GatewayEvent(GatewayEvent.Type.PAYMENT_FAILED, toPayment(payment), null, null));
                }
            }
            case "refund.processed", "refund.failed" -> {
                if (refund != null) {
                    events.add(new GatewayEvent("refund.processed".equals(eventType)
                            ? GatewayEvent.Type.REFUND_PROCESSED : GatewayEvent.Type.REFUND_FAILED,
                            null, refund.optString("payment_id", null), refund.optString("id", null)));
                }
            }
            default -> log.debug("razorpay.webhook.ignored event={}", eventType);
        }
        return new WebhookParseResult(true, headers.get("x-razorpay-event-id"), eventType, payloadJson, events);
    }

    @Override
    public ProviderRefund refund(String providerOrderId, String providerPaymentId, long amountPaise, String reference) {
        JSONObject request = new JSONObject();
        request.put("amount", amountPaise);
        request.put("speed", "normal");
        request.put("receipt", reference);
        try {
            Refund refund = client().payments.refund(providerPaymentId, request);
            JSONObject json = refund.toJson();
            String status = json.optString("status", "pending");
            log.info("razorpay.refund_created refundId={} paymentId={} status={}", json.optString("id"), providerPaymentId, status);
            return new ProviderRefund(json.optString("id", null), "processed".equals(status));
        } catch (RazorpayException e) {
            throw providerError("refund", e);
        }
    }

    private static JSONObject entity(JSONObject payload, String key) {
        JSONObject wrapper = payload.optJSONObject(key);
        return wrapper == null ? null : wrapper.optJSONObject("entity");
    }

    static ProviderPayment toPayment(JSONObject json) {
        String status = json.optString("status", "");
        ProviderPayment.Outcome outcome = switch (status) {
            case "captured" -> ProviderPayment.Outcome.CAPTURED;
            case "authorized" -> ProviderPayment.Outcome.AUTHORIZED;
            case "failed" -> ProviderPayment.Outcome.FAILED;
            default -> ProviderPayment.Outcome.PENDING;
        };
        return new ProviderPayment(json.optString("order_id", null), json.optString("id", null), outcome,
                json.optLong("amount", 0L), json.optString("method", null),
                json.isNull("error_description") ? null : json.optString("error_description", null));
    }

    private RazorpayClient client() {
        RazorpayClient c = client;
        if (c == null) {
            synchronized (this) {
                if (client == null) {
                    if (!props.isConfigured()) {
                        throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENTS_NOT_CONFIGURED",
                                "Online payment is not configured");
                    }
                    try {
                        client = new RazorpayClient(props.keyId(), props.keySecret());
                    } catch (RazorpayException e) {
                        throw providerError("init", e);
                    }
                }
                c = client;
            }
        }
        return c;
    }

    private static ApiException providerError(String action, RazorpayException e) {
        // SDK messages never include credentials, so they are safe to log.
        log.error("razorpay.error action={} message={}", action, e.getMessage());
        return new ApiException(HttpStatus.BAD_GATEWAY, "PAYMENT_PROVIDER_ERROR",
                "Payment provider is unavailable. Please try again.");
    }
}
