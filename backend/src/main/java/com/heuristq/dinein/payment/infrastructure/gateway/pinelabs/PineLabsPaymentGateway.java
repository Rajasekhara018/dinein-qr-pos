package com.heuristq.dinein.payment.infrastructure.gateway.pinelabs;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heuristq.dinein.payment.infrastructure.gateway.CheckoutContext;
import com.heuristq.dinein.payment.infrastructure.gateway.CheckoutMode;
import com.heuristq.dinein.payment.infrastructure.gateway.CheckoutPayload;
import com.heuristq.dinein.payment.infrastructure.gateway.ClientVerification;
import com.heuristq.dinein.payment.infrastructure.gateway.PaymentGateway;
import com.heuristq.dinein.payment.infrastructure.gateway.ProviderPayment;
import com.heuristq.dinein.payment.infrastructure.gateway.ProviderRefund;
import com.heuristq.dinein.payment.infrastructure.gateway.WebhookParseResult;
import com.heuristq.dinein.payment.infrastructure.gateway.WebhookParseResult.GatewayEvent;
import com.heuristq.dinein.shared.exception.ApiException;
import com.heuristq.dinein.shared.util.SecureTokens;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Pine Labs Online (Plural) hosted checkout ({@link CheckoutMode#REDIRECT}).
 * <ul>
 *   <li>OAuth client-credentials token ({@code POST /api/auth/v1/token}), cached until shortly before it expires.</li>
 *   <li>{@code POST /api/checkout/v1/orders} creates the order and returns {@code order_id} + {@code redirect_url}.</li>
 *   <li>Pine Labs sends the guest back to our callback URL; the order id from that redirect is only a hint, and the
 *       result is always read from {@code GET /api/pay/v1/orders/{order_id}} before anything is marked paid.</li>
 *   <li>Webhooks ({@code ORDER_PROCESSED}, {@code ORDER_FAILED}, {@code PAYMENT_FAILED}, {@code REFUND_*}) are
 *       verified with {@link PineLabsSignatures} over the raw body.</li>
 *   <li>Refunds: {@code POST /api/pay/v1/refunds/{order_id}}.</li>
 * </ul>
 * Amounts are paise at the API edge. Every request carries {@code Request-ID} and {@code Request-Timestamp}.
 */
@Slf4j
@Component
public class PineLabsPaymentGateway implements PaymentGateway {

    public static final String CODE = "PINELABS";

    static final String TOKEN_PATH = "/api/auth/v1/token";
    static final String CHECKOUT_PATH = "/api/checkout/v1/orders";
    static final String ORDER_PATH = "/api/pay/v1/orders/";
    static final String REFUND_PATH = "/api/pay/v1/refunds/";

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(20);
    /** Refresh the token this long before it expires (capped at half its lifetime). */
    private static final Duration TOKEN_REFRESH_SKEW = Duration.ofSeconds(60);
    /** Redirect links are kept in memory for retries; after a restart they are recovered from the API. */
    private static final Duration LINK_TTL = Duration.ofHours(2);
    private static final int LINK_CACHE_MAX = 1000;
    /** Query parameter on our callback URL carrying our order id. */
    static final String ORDER_PARAM = "dinein_order";
    private static final Pattern ORDER_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    private final PineLabsProperties props;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final Clock clock;
    private final Object tokenLock = new Object();
    private final Map<String, CachedLink> links = new ConcurrentHashMap<>();
    private volatile AccessToken token;

    @Autowired
    public PineLabsPaymentGateway(PineLabsProperties props, ObjectMapper objectMapper,
                                  RestClient.Builder restClientBuilder, Clock clock) {
        this(props, objectMapper, restClientBuilder.clone().requestFactory(requestFactory()).build(), clock);
    }

    /** For tests: a pre-built client (e.g. bound to {@code MockRestServiceServer}). */
    PineLabsPaymentGateway(PineLabsProperties props, ObjectMapper objectMapper, RestClient restClient, Clock clock) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.restClient = restClient;
        this.clock = clock;
    }

    private static SimpleClientHttpRequestFactory requestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        return factory;
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public boolean isConfigured() {
        return props.isConfigured();
    }

    /**
     * A Pine Labs order can see several attempts, but once we have recorded a failure the guest gets a fresh order
     * (and a fresh link) so the retry never lands on a checkout that Pine Labs may already have closed.
     */
    @Override
    public boolean reusableAfterFailure() {
        return false;
    }

    @Override
    public String createProviderOrder(CheckoutContext ctx) {
        // Unique per attempt: the checkout API returns the existing order for a repeated reference.
        // VERIFY: allowed charset (docs give 1-50 chars); we send only [A-Za-z0-9-].
        String reference = ("DI" + ctx.orderId() + "-" + SecureTokens.randomUrlSafe(9)).replaceAll("[^A-Za-z0-9-]", "0");
        JsonNode response = send("create order", HttpMethod.POST, CHECKOUT_PATH, orderRequest(ctx, reference));
        String orderId = text(response, "order_id", "orderId");
        String redirectUrl = text(response, "redirect_url", "redirectUrl");
        if (orderId.isBlank() || redirectUrl.isBlank() || orderId.length() > 64) {
            log.error("pinelabs.order.bad_response reference={} code={} message={}", reference,
                    response.path("response_code").asText(), response.path("response_message").asText());
            throw unavailable();
        }
        remember(orderId, redirectUrl);
        log.info("pinelabs.order_created pinelabsOrderId={} reference={} amountPaise={}", orderId, reference, ctx.amountPaise());
        return orderId;
    }

    @Override
    public CheckoutPayload checkoutPayload(CheckoutContext ctx) {
        String url = cachedLink(ctx.providerOrderId());
        if (url == null) {
            url = recoverLink(ctx);
        }
        return new CheckoutPayload(CheckoutMode.REDIRECT, Map.of("url", url));
    }

    /**
     * The redirect back from Pine Labs is not trusted for anything but the order id: whatever status or signature it
     * carries, {@link com.heuristq.dinein.payment.PaymentService#verifyCallback} reads the authoritative result from
     * the order status API (and the order id must belong to one of our payment rows).
     * The redirect is unsigned (confirmed against the owner's production Pine Labs integration); if Pine Labs ever adds one for this
     * merchant account, verify it here as an extra check. The payment id is taken from the status API, never the
     * browser.
     */
    @Override
    public ClientVerification verifyClientCallback(Map<String, String> params) {
        String orderId = firstNonBlank(params.get("order_id"), params.get("orderId"));
        if (orderId == null) {
            String ours = params.get(ORDER_PARAM);
            if (ours != null && ours.matches("\\d{1,18}")) {
                log.info("pinelabs.callback orderId={} status={}", ours, params.get("status"));
                return ClientVerification.forInternalOrder(Long.parseLong(ours));
            }
        }
        if (orderId == null || !ORDER_ID.matcher(orderId).matches()) {
            log.warn("pinelabs.callback.missing_order_id keys={}", params.keySet());
            throw ApiException.badRequest("INVALID_SIGNATURE", "Payment could not be verified");
        }
        log.info("pinelabs.callback pinelabsOrderId={} status={}", orderId, params.get("status"));
        return new ClientVerification(orderId, null);
    }

    @Override
    public ProviderPayment fetchPayment(String providerOrderId, String providerPaymentId) {
        List<ProviderPayment> payments = fetchOrderPayments(providerOrderId);
        if (providerPaymentId != null) {
            for (ProviderPayment p : payments) {
                if (providerPaymentId.equals(p.providerPaymentId())) {
                    return p;
                }
            }
        }
        return mostRelevant(payments)
                .orElse(new ProviderPayment(providerOrderId, providerPaymentId, ProviderPayment.Outcome.PENDING, 0, null, null));
    }

    @Override
    public List<ProviderPayment> fetchOrderPayments(String providerOrderId) {
        if (providerOrderId == null || !ORDER_ID.matcher(providerOrderId).matches()) {
            throw ApiException.badRequest("INVALID_PROVIDER_ORDER", "Invalid provider order id");
        }
        return toPayments(unwrap(send("fetch order", HttpMethod.GET, ORDER_PATH + providerOrderId, null)));
    }

    @Override
    public WebhookParseResult parseWebhook(byte[] rawBody, Map<String, String> headers) {
        JsonNode json;
        try {
            json = objectMapper.readTree(rawBody);
        } catch (IOException e) {
            json = null;
        }
        String eventType = json == null ? null : json.path("event_type").asText(null);
        String payloadJson = json == null || !json.isObject() ? quote(rawBody) : new String(rawBody, StandardCharsets.UTF_8);
        String webhookId = headers.get("webhook-id");
        if (!PineLabsSignatures.verifyWebhook(rawBody, webhookId, headers.get("webhook-timestamp"),
                headers.get("webhook-signature"), props.webhookSecret(), clock.instant(), props.webhookTolerance())) {
            return WebhookParseResult.invalid(eventType == null ? "pinelabs.unknown" : eventType, payloadJson);
        }
        JsonNode data = json == null ? objectMapper.createObjectNode() : json.path("data");
        List<GatewayEvent> events = new ArrayList<>();
        String merchantId = data.path("merchant_id").asText("");
        if (PineLabsProperties.notBlank(props.merchantId()) && !merchantId.isEmpty() && !props.merchantId().trim().equals(merchantId)) {
            log.warn("pinelabs.webhook.other_merchant event={} merchantId={}", eventType, merchantId);
            return new WebhookParseResult(true, webhookId, eventType, payloadJson, events);
        }
        boolean refundOrder = "REFUND".equalsIgnoreCase(data.path("type").asText(""));
        switch (eventType == null ? "" : eventType) {
            case "ORDER_PROCESSED" -> {
                if (!refundOrder) {
                    pick(toPayments(data), ProviderPayment.Outcome.CAPTURED).ifPresent(p ->
                            events.add(new GatewayEvent(GatewayEvent.Type.PAYMENT_CAPTURED, p, null, null)));
                }
            }
            case "ORDER_AUTHORIZED" -> {
                if (!refundOrder) {
                    pick(toPayments(data), ProviderPayment.Outcome.AUTHORIZED).ifPresent(p ->
                            events.add(new GatewayEvent(GatewayEvent.Type.PAYMENT_AUTHORIZED, p, null, null)));
                }
            }
            case "ORDER_FAILED", "ORDER_CANCELLED", "PAYMENT_FAILED" -> {
                if (!refundOrder) {
                    pick(toPayments(data), ProviderPayment.Outcome.FAILED).ifPresent(p ->
                            events.add(new GatewayEvent(GatewayEvent.Type.PAYMENT_FAILED, p, null, null)));
                }
            }
            case "REFUND_PROCESSED", "REFUND_FAILED" -> {
                // data.order_id is the refund order; parent_order_id is the order that was charged. Our payment row
                // stores the charge's payment id, so it is read from the parent order.
                String parent = data.path("parent_order_id").asText("");
                if (!parent.isBlank()) {
                    events.add(new GatewayEvent("REFUND_PROCESSED".equals(eventType)
                            ? GatewayEvent.Type.REFUND_PROCESSED : GatewayEvent.Type.REFUND_FAILED,
                            null, chargePaymentId(parent), data.path("order_id").asText(null)));
                }
            }
            default -> log.debug("pinelabs.webhook.ignored event={}", eventType);
        }
        return new WebhookParseResult(true, webhookId, eventType, payloadJson, events);
    }

    /**
     * {@code merchant_order_reference} is Pine Labs' idempotency key for refunds (a repeat returns the existing refund).
     * A random suffix lets an admin retry after a <em>failed</em> refund; a second full refund of the same order is
     * refused by Pine Labs because nothing refundable is left.
     */
    @Override
    public ProviderRefund refund(String providerOrderId, String providerPaymentId, long amountPaise, String reference) {
        String ref = (reference + "-" + SecureTokens.randomUrlSafe(6)).replaceAll("[^A-Za-z0-9-]", "0");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("merchant_order_reference", ref.length() > 50 ? ref.substring(0, 50) : ref);
        body.put("order_amount", Map.of("value", amountPaise, "currency", "INR"));
        body.put("merchant_metadata", Map.of("reference", reference));
        JsonNode data = unwrap(send("refund", HttpMethod.POST, REFUND_PATH + providerOrderId, body));
        String status = data.path("status").asText("").toUpperCase(Locale.ROOT);
        String refundId = data.path("order_id").asText(null);
        if ("FAILED".equals(status)) {
            log.error("pinelabs.refund.rejected pinelabsOrderId={} refundId={}", providerOrderId, refundId);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "REFUND_FAILED", "Refund was rejected by the payment provider");
        }
        log.info("pinelabs.refund_created refundId={} pinelabsOrderId={} status={}", refundId, providerOrderId, status);
        return new ProviderRefund(refundId, "PROCESSED".equals(status));
    }

    // --- mapping ------------------------------------------------------------------------------------------------

    /** Payment status -> neutral outcome. Pine Labs: PENDING, PROCESSED, FAILED, AUTHORIZED, CANCELLED. */
    static ProviderPayment.Outcome outcome(String status) {
        return switch (status == null ? "" : status.toUpperCase(Locale.ROOT)) {
            case "PROCESSED" -> ProviderPayment.Outcome.CAPTURED;
            case "AUTHORIZED" -> ProviderPayment.Outcome.AUTHORIZED;
            case "FAILED", "CANCELLED" -> ProviderPayment.Outcome.FAILED;
            default -> ProviderPayment.Outcome.PENDING;
        };
    }

    /**
     * Payments of an order object ({@code data} of the status API or a webhook). An order that Pine Labs closed as
     * FAILED/CANCELLED without a failed attempt yields one synthetic failed payment, so reconciliation sees the failure.
     */
    static List<ProviderPayment> toPayments(JsonNode order) {
        String orderId = firstNonBlank(text(order, "order_id", "orderId"), null);
        // Amount fallbacks: some responses only carry the order-level amount (the owner's production integration
        // reads data.amount). A captured payment must never be read as 0, or it would be flagged as a mismatch.
        long orderAmount = order.path("order_amount").path("value").asLong(order.path("amount").asLong(0));
        List<ProviderPayment> payments = new ArrayList<>();
        for (JsonNode p : order.path("payments")) {
            JsonNode error = p.path("error_detail");
            String errorMessage = error.hasNonNull("message") ? error.path("message").asText() : null;
            String rawMethod = firstNonBlank(text(p, "payment_method", "paymentMethod"), null);
            String method = rawMethod == null ? null : rawMethod.toLowerCase(Locale.ROOT);
            long amount = p.path("payment_amount").path("value").asLong(p.path("amount").asLong(orderAmount));
            payments.add(new ProviderPayment(orderId, p.path("id").asText(null), outcome(p.path("status").asText()),
                    amount, method, errorMessage));
        }
        String orderStatus = order.path("status").asText("").toUpperCase(Locale.ROOT);
        boolean closedAsFailed = "FAILED".equals(orderStatus) || "CANCELLED".equals(orderStatus);
        if (closedAsFailed && payments.stream().noneMatch(p -> p.outcome() == ProviderPayment.Outcome.FAILED)) {
            payments.add(new ProviderPayment(orderId, null, ProviderPayment.Outcome.FAILED, 0, null,
                    "Order " + orderStatus.toLowerCase(Locale.ROOT)));
        }
        return payments;
    }

    /** Captured beats authorized beats pending beats failed; among equals the last attempt wins. */
    static Optional<ProviderPayment> mostRelevant(List<ProviderPayment> payments) {
        Comparator<ProviderPayment> rank = Comparator.comparingInt(p -> switch (p.outcome()) {
            case CAPTURED -> 3;
            case AUTHORIZED -> 2;
            case PENDING -> 1;
            case FAILED -> 0;
        });
        ProviderPayment best = null;
        for (ProviderPayment p : payments) {
            if (best == null || rank.compare(p, best) >= 0) {
                best = p;
            }
        }
        return Optional.ofNullable(best);
    }

    private static Optional<ProviderPayment> pick(List<ProviderPayment> payments, ProviderPayment.Outcome wanted) {
        ProviderPayment found = null;
        for (ProviderPayment p : payments) {
            if (p.outcome() == wanted) {
                found = p;
            }
        }
        return Optional.ofNullable(found);
    }

    private String chargePaymentId(String parentOrderId) {
        List<ProviderPayment> payments = fetchOrderPayments(parentOrderId);
        return pick(payments, ProviderPayment.Outcome.CAPTURED)
                .or(() -> payments.stream().filter(p -> p.providerPaymentId() != null).findFirst())
                .map(ProviderPayment::providerPaymentId).orElse(null);
    }

    // --- checkout links -----------------------------------------------------------------------------------------

    private Map<String, Object> orderRequest(CheckoutContext ctx, String reference) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("merchant_order_reference", reference);
        body.put("order_amount", Map.of("value", ctx.amountPaise(), "currency", ctx.currency()));
        body.put("pre_auth", false);
        body.put("integration_mode", "REDIRECT");
        // The redirect back is unsigned and may not carry Pine Labs' order id, so the callback URL carries ours
        // (same approach as the owner's production Pine Labs integration). It is only a lookup key: the result is
        // always read from the order status API.
        String callbackUrl = ctx.callbackUrl() + (ctx.callbackUrl().contains("?") ? "&" : "?")
                + ORDER_PARAM + "=" + ctx.orderId();
        body.put("callback_url", callbackUrl);
        // VERIFY: failure_callback_url appears in Pine Labs order payloads; confirm hosted checkout accepts it.
        body.put("failure_callback_url", callbackUrl);
        body.put("allowed_payment_methods", List.of("CARD", "UPI", "NETBANKING", "WALLET"));
        body.put("notes", "Order " + ctx.orderNumber());
        Map<String, Object> customer = new LinkedHashMap<>();
        if (ctx.customerName() != null && !ctx.customerName().isBlank()) {
            String name = ctx.customerName().replaceAll("[^\\p{L}\\p{N} .'-]", " ").trim();
            customer.put("first_name", name.length() > 50 ? name.substring(0, 50) : name);
        }
        if (ctx.customerPhone() != null && ctx.customerPhone().matches("\\d{10}")) {
            customer.put("mobile_number", ctx.customerPhone());
            customer.put("country_code", "91");
        }
        Map<String, Object> purchase = new LinkedHashMap<>();
        if (!customer.isEmpty()) {
            purchase.put("customer", customer);
        }
        purchase.put("merchant_metadata", Map.of("order_id", String.valueOf(ctx.orderId()),
                "order_number", String.valueOf(ctx.orderNumber())));
        body.put("purchase_details", purchase);
        return body;
    }

    /**
     * After a restart the in-memory link is gone. {@code merchant_order_reference} is the checkout API's idempotency
     * key, so re-posting the original reference returns the same order and its link.
     * VERIFY: that the idempotent replay response includes {@code redirect_url} (the docs say it "returns the
     * existing order").
     */
    private String recoverLink(CheckoutContext ctx) {
        String providerOrderId = ctx.providerOrderId();
        if (providerOrderId == null || !ORDER_ID.matcher(providerOrderId).matches()) {
            throw unavailable();
        }
        JsonNode order = unwrap(send("fetch order", HttpMethod.GET, ORDER_PATH + providerOrderId, null));
        String reference = order.path("merchant_order_reference").asText("");
        if (reference.isBlank()) {
            log.error("pinelabs.link_recovery.no_reference pinelabsOrderId={}", providerOrderId);
            throw unavailable();
        }
        JsonNode response = send("recover link", HttpMethod.POST, CHECKOUT_PATH, orderRequest(ctx, reference));
        String orderId = text(response, "order_id", "orderId");
        String redirectUrl = text(response, "redirect_url", "redirectUrl");
        if (!providerOrderId.equals(orderId) || redirectUrl.isBlank()) {
            log.error("pinelabs.link_recovery.failed pinelabsOrderId={} returnedOrderId={}", providerOrderId, orderId);
            throw unavailable();
        }
        remember(orderId, redirectUrl);
        return redirectUrl;
    }

    private void remember(String orderId, String url) {
        Instant now = clock.instant();
        if (links.size() >= LINK_CACHE_MAX) {
            links.entrySet().removeIf(e -> e.getValue().expiresAt().isBefore(now));
            if (links.size() >= LINK_CACHE_MAX) {
                links.clear();
            }
        }
        links.put(orderId, new CachedLink(url, now.plus(LINK_TTL)));
    }

    private String cachedLink(String orderId) {
        CachedLink link = orderId == null ? null : links.get(orderId);
        if (link == null) {
            return null;
        }
        if (link.expiresAt().isBefore(clock.instant())) {
            links.remove(orderId);
            return null;
        }
        return link.url();
    }

    // --- HTTP ---------------------------------------------------------------------------------------------------

    /** Sends an authenticated request; a 401 (token revoked early) refreshes the token and retries once. */
    private JsonNode send(String action, HttpMethod method, String path, Object body) {
        if (!isConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENTS_NOT_CONFIGURED", "Pine Labs is not configured");
        }
        try {
            try {
                return exchange(method, path, body, accessToken());
            } catch (HttpClientErrorException.Unauthorized e) {
                log.warn("pinelabs.unauthorized action={} refreshing token", action);
                invalidateToken();
                return exchange(method, path, body, accessToken());
            }
        } catch (RestClientException e) {
            throw providerError(action, e);
        }
    }

    private JsonNode exchange(HttpMethod method, String path, Object body, String bearer) {
        RestClient.RequestBodySpec spec = restClient.method(method).uri(props.apiBase() + path)
                .headers(h -> {
                    if (bearer != null) {
                        h.setBearerAuth(bearer);
                    }
                    h.setAccept(List.of(MediaType.APPLICATION_JSON));
                    h.set("Request-ID", UUID.randomUUID().toString());
                    h.set("Request-Timestamp", clock.instant().truncatedTo(ChronoUnit.MILLIS).toString());
                });
        if (body != null) {
            spec.contentType(MediaType.APPLICATION_JSON).body(toJson(body));
        }
        String response = spec.retrieve().body(String.class);
        try {
            return objectMapper.readTree(response == null || response.isBlank() ? "{}" : response);
        } catch (JsonProcessingException e) {
            throw new RestClientException("Unparseable response", e);
        }
    }

    String accessToken() {
        AccessToken t = token;
        if (t != null && clock.instant().isBefore(t.refreshAt())) {
            return t.value();
        }
        synchronized (tokenLock) {
            t = token;
            if (t == null || !clock.instant().isBefore(t.refreshAt())) {
                t = fetchToken();
                token = t;
            }
            return t.value();
        }
    }

    private void invalidateToken() {
        synchronized (tokenLock) {
            token = null;
        }
    }

    private AccessToken fetchToken() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("client_id", props.clientId());
        body.put("client_secret", props.clientSecret());
        body.put("grant_type", "client_credentials");
        JsonNode response = exchange(HttpMethod.POST, TOKEN_PATH, body, null);
        String value = response.path("access_token").asText("");
        if (value.isBlank()) {
            throw new RestClientException("Token response without access_token");
        }
        Instant now = clock.instant();
        Instant expiresAt = expiry(response, now);
        Duration lifetime = Duration.between(now, expiresAt);
        Duration skew = lifetime.dividedBy(2).compareTo(TOKEN_REFRESH_SKEW) < 0 ? lifetime.dividedBy(2) : TOKEN_REFRESH_SKEW;
        log.info("pinelabs.token_refreshed expiresAt={}", expiresAt);
        return new AccessToken(value, expiresAt.minus(skew));
    }

    /** {@code expires_at} is an ISO-8601 timestamp; {@code expires_in} seconds is accepted too; else 5 minutes. */
    private static Instant expiry(JsonNode response, Instant now) {
        String at = response.path("expires_at").asText("");
        if (!at.isBlank()) {
            try {
                return OffsetDateTime.parse(at).toInstant();
            } catch (DateTimeParseException e) {
                try {
                    return Instant.parse(at.endsWith("Z") ? at : at + "Z");
                } catch (DateTimeParseException ignored) {
                    // fall through
                }
            }
        }
        long in = response.path("expires_in").asLong(0);
        return now.plusSeconds(in > 0 ? in : 300);
    }

    private String toJson(Object body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The status/refund APIs wrap the order in {@code data}; checkout responses are flat. */
    private static JsonNode unwrap(JsonNode node) {
        JsonNode data = node.path("data");
        return data.isObject() ? data : node;
    }

    private String quote(byte[] raw) {
        try {
            return objectMapper.writeValueAsString(new String(raw, StandardCharsets.UTF_8));
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    /** Reads a string field, accepting Pine Labs' documented snake_case or the camelCase seen from some sandboxes. */
    private static String text(JsonNode node, String snake, String camel) {
        String value = node.path(snake).asText("");
        return value.isBlank() ? node.path(camel).asText("") : value;
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a.trim();
        }
        return b == null || b.isBlank() ? null : b.trim();
    }

    private static ApiException providerError(String action, RestClientException e) {
        // Response bodies are error descriptions (never our secret); truncated to keep logs small.
        if (e instanceof RestClientResponseException r) {
            String body = r.getResponseBodyAsString();
            log.error("pinelabs.error action={} status={} body={}", action, r.getStatusCode().value(),
                    body.length() > 300 ? body.substring(0, 300) : body);
        } else {
            log.error("pinelabs.error action={} message={}", action, e.getMessage());
        }
        return unavailable();
    }

    private static ApiException unavailable() {
        return new ApiException(HttpStatus.BAD_GATEWAY, "PAYMENT_PROVIDER_ERROR",
                "Payment provider is unavailable. Please try again.");
    }

    private record AccessToken(String value, Instant refreshAt) {
    }

    private record CachedLink(String url, Instant expiresAt) {
    }
}
