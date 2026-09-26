package com.heuristq.dinein.payment.gateway.payu;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.heuristq.dinein.shared.util.Money;
import com.heuristq.dinein.shared.util.SecureTokens;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * PayU hosted checkout ({@link CheckoutMode#FORM_POST}). The browser posts a hashed form to PayU; PayU posts the
 * result back to our callback URL (surl/furl) and to the configured webhook. Every result is hash-verified and then
 * confirmed with the {@code verify_payment} API before an order is marked paid.
 */
@Slf4j
@Component
public class PayuPaymentGateway implements PaymentGateway {

    public static final String CODE = "PAYU";
    private static final String VERIFY_PAYMENT = "verify_payment";
    private static final String REFUND = "cancel_refund_transaction";

    private final PayuProperties props;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public PayuPaymentGateway(PayuProperties props, ObjectMapper objectMapper, RestClient.Builder restClientBuilder) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.restClient = restClientBuilder.build();
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public boolean isConfigured() {
        return props.isConfigured();
    }

    /** A PayU txnid can only be attempted once, so every retry gets a new one. */
    @Override
    public boolean reusableAfterFailure() {
        return false;
    }

    /** PayU has no server-side order: the txnid is allocated locally (max 25 chars, unique). */
    @Override
    public String createProviderOrder(CheckoutContext ctx) {
        return ("DI" + ctx.orderId() + "X" + SecureTokens.randomUrlSafe(9)).replaceAll("[^A-Za-z0-9]", "0");
    }

    @Override
    public CheckoutPayload checkoutPayload(CheckoutContext ctx) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("key", props.key());
        fields.put("txnid", ctx.providerOrderId());
        fields.put("amount", Money.fromPaise(ctx.amountPaise()).toPlainString());
        fields.put("productinfo", "Order " + ctx.orderNumber());
        fields.put("firstname", ctx.customerName() == null ? "Guest" : ctx.customerName().replaceAll("[|]", " "));
        fields.put("email", props.defaultEmail());
        if (ctx.customerPhone() != null) {
            fields.put("phone", ctx.customerPhone());
        }
        fields.put("surl", ctx.callbackUrl());
        fields.put("furl", ctx.callbackUrl());
        fields.put("udf1", String.valueOf(ctx.orderId()));
        fields.put("hash", PayuHash.request(props.key(), props.salt(), fields));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("action", props.baseUrl() + "/_payment");
        data.put("method", "POST");
        data.put("fields", fields);
        return new CheckoutPayload(CheckoutMode.FORM_POST, data);
    }

    @Override
    public ClientVerification verifyClientCallback(Map<String, String> params) {
        if (!PayuHash.verifyResponse(props.key(), props.salt(), params)) {
            log.warn("payu.callback.invalid_hash txnid={}", params.get("txnid"));
            throw ApiException.badRequest("INVALID_SIGNATURE", "Payment could not be verified");
        }
        return new ClientVerification(params.get("txnid"), params.get("mihpayid"));
    }

    @Override
    public ProviderPayment fetchPayment(String providerOrderId, String providerPaymentId) {
        return fetchOrderPayments(providerOrderId).stream().findFirst()
                .orElse(new ProviderPayment(providerOrderId, providerPaymentId, ProviderPayment.Outcome.PENDING, 0, null, null));
    }

    @Override
    public List<ProviderPayment> fetchOrderPayments(String providerOrderId) {
        JsonNode response = postService(VERIFY_PAYMENT, providerOrderId, Map.of());
        JsonNode txn = response.path("transaction_details").path(providerOrderId);
        if (txn.isMissingNode() || txn.isNull() || "Not Found".equalsIgnoreCase(txn.path("status").asText())) {
            return List.of();
        }
        return List.of(toPayment(providerOrderId, txn));
    }

    @Override
    public WebhookParseResult parseWebhook(byte[] rawBody, Map<String, String> headers) {
        Map<String, String> fields = parseBody(rawBody, headers.getOrDefault("content-type", ""));
        String payloadJson = toJson(fields);
        String status = fields.getOrDefault("status", "");
        if (!PayuHash.verifyResponse(props.key(), props.salt(), fields)) {
            return WebhookParseResult.invalid("payu." + status, payloadJson);
        }
        ProviderPayment payment = new ProviderPayment(fields.get("txnid"), fields.get("mihpayid"), outcome(status),
                toPaise(fields.get("amount")), lower(fields.get("mode")), fields.get("error_Message"));
        GatewayEvent.Type type = switch (payment.outcome()) {
            case CAPTURED -> GatewayEvent.Type.PAYMENT_CAPTURED;
            case FAILED -> GatewayEvent.Type.PAYMENT_FAILED;
            default -> null;
        };
        String eventId = fields.get("mihpayid") == null ? null : fields.get("mihpayid") + ":" + status;
        return new WebhookParseResult(true, eventId, "payu." + status, payloadJson,
                type == null ? List.of() : List.of(new GatewayEvent(type, payment, null, null)));
    }

    @Override
    public ProviderRefund refund(String providerOrderId, String providerPaymentId, long amountPaise, String reference) {
        Map<String, String> extra = new LinkedHashMap<>();
        extra.put("var2", reference);
        extra.put("var3", Money.fromPaise(amountPaise).toPlainString());
        JsonNode response = postService(REFUND, providerPaymentId, extra);
        if (response.path("status").asInt(0) != 1) {
            log.error("payu.refund.rejected paymentId={} msg={}", providerPaymentId, response.path("msg").asText());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "REFUND_FAILED", "Refund was rejected by the payment provider");
        }
        String requestId = response.path("request_id").asText(null);
        log.info("payu.refund.queued paymentId={} requestId={}", providerPaymentId, requestId);
        return new ProviderRefund(requestId, false);
    }

    private JsonNode postService(String command, String var1, Map<String, String> extra) {
        if (!isConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENTS_NOT_CONFIGURED", "PayU is not configured");
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("key", props.key());
        form.add("command", command);
        form.add("var1", var1);
        extra.forEach(form::add);
        form.add("hash", PayuHash.command(props.key(), command, var1, props.salt()));
        try {
            String body = restClient.post().uri(props.postServiceUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(String.class);
            return objectMapper.readTree(body == null ? "{}" : body);
        } catch (RestClientException | java.io.IOException e) {
            log.error("payu.error command={} message={}", command, e.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "PAYMENT_PROVIDER_ERROR",
                    "Payment provider is unavailable. Please try again.");
        }
    }

    private static ProviderPayment toPayment(String txnid, JsonNode txn) {
        String amount = txn.hasNonNull("transaction_amount") ? txn.path("transaction_amount").asText() : txn.path("amt").asText();
        return new ProviderPayment(txnid, txn.path("mihpayid").asText(null), outcome(txn.path("status").asText()),
                toPaise(amount), lower(txn.path("mode").asText(null)), txn.path("error_Message").asText(null));
    }

    private static ProviderPayment.Outcome outcome(String status) {
        return switch (status == null ? "" : status.toLowerCase(Locale.ROOT)) {
            case "success", "captured" -> ProviderPayment.Outcome.CAPTURED;
            case "failure", "failed", "dropped", "bounced", "usercancelled" -> ProviderPayment.Outcome.FAILED;
            default -> ProviderPayment.Outcome.PENDING;
        };
    }

    private static long toPaise(String rupees) {
        try {
            return rupees == null || rupees.isBlank() ? 0 : Money.toPaise(new BigDecimal(rupees.trim()));
        } catch (NumberFormatException | ArithmeticException e) {
            return 0;
        }
    }

    private static String lower(String s) {
        return s == null ? null : s.toLowerCase(Locale.ROOT);
    }

    private Map<String, String> parseBody(byte[] raw, String contentType) {
        String body = new String(raw, StandardCharsets.UTF_8);
        Map<String, String> fields = new LinkedHashMap<>();
        if (contentType.contains("json")) {
            try {
                objectMapper.readTree(body).properties().forEach(e -> fields.put(e.getKey(), e.getValue().asText()));
            } catch (java.io.IOException e) {
                return fields;
            }
            return fields;
        }
        for (String pair : body.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                fields.put(URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
                        URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
            }
        }
        return fields;
    }

    private String toJson(Map<String, String> fields) {
        try {
            Map<String, String> safe = new LinkedHashMap<>(fields);
            safe.remove("hash");
            return objectMapper.writeValueAsString(safe);
        } catch (java.io.IOException e) {
            return "{}";
        }
    }
}
