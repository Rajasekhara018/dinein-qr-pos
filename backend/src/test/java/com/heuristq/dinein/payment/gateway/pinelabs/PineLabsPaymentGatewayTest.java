package com.heuristq.dinein.payment.gateway.pinelabs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heuristq.dinein.payment.gateway.CheckoutContext;
import com.heuristq.dinein.payment.gateway.CheckoutMode;
import com.heuristq.dinein.payment.gateway.CheckoutPayload;
import com.heuristq.dinein.payment.gateway.ProviderPayment;
import com.heuristq.dinein.payment.gateway.ProviderRefund;
import com.heuristq.dinein.payment.gateway.WebhookParseResult;
import com.heuristq.dinein.payment.gateway.WebhookParseResult.GatewayEvent;
import com.heuristq.dinein.shared.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PineLabsPaymentGatewayTest {

    private static final String BASE = "https://pl.test";
    private static final String SECRET = Base64.getEncoder().encodeToString("unit-test-webhook-secret-bytes!!".getBytes(StandardCharsets.UTF_8));
    private static final org.hamcrest.Matcher<String> NOT_BLANK = org.hamcrest.Matchers.not(org.hamcrest.Matchers.emptyOrNullString());
    private static final String ORDER_ID = "v1-250101-aa-AbCd12";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T10:00:00Z"));
    private final PineLabsProperties props =
            new PineLabsProperties(null, "client-id", "client-secret", SECRET, BASE + "/", Duration.ofMinutes(5));
    private MockRestServiceServer server;
    private PineLabsPaymentGateway gateway;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        gateway = new PineLabsPaymentGateway(props, objectMapper, builder.build(), clock);
    }

    private CheckoutContext ctx(String providerOrderId) {
        return new CheckoutContext(42L, "A-0042", 50000, "INR", "Asha K", "9876543210", "Cafe", "#000000",
                providerOrderId, "https://dinein.test/api/public/payments/pinelabs/callback", "https://dinein.test/menu/orders/42");
    }

    private void expectToken(String token, Instant expiresAt) {
        server.expect(requestTo(BASE + PineLabsPaymentGateway.TOKEN_PATH))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Request-ID", NOT_BLANK))
                .andExpect(header("Request-Timestamp", NOT_BLANK))
                .andExpect(jsonPath("$.client_id").value("client-id"))
                .andExpect(jsonPath("$.client_secret").value("client-secret"))
                .andExpect(jsonPath("$.grant_type").value("client_credentials"))
                .andRespond(withSuccess("{\"access_token\":\"" + token + "\",\"expires_at\":\"" + expiresAt + "\"}",
                        MediaType.APPLICATION_JSON));
    }

    private void expectOrderFetch(String token, String body) {
        server.expect(requestTo(BASE + PineLabsPaymentGateway.ORDER_PATH + ORDER_ID))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer " + token))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    private static String orderJson(String orderStatus, String paymentStatus) {
        return """
                {"data":{"order_id":"%s","merchant_order_reference":"DI42-ref","type":"CHARGE","status":"%s",
                 "order_amount":{"value":50000,"currency":"INR"},
                 "payments":[{"id":"%s-cc-1","status":"%s","payment_amount":{"value":50000,"currency":"INR"},
                              "payment_method":"UPI"}]}}
                """.formatted(ORDER_ID, orderStatus, ORDER_ID, paymentStatus);
    }

    @Test
    void createsHostedCheckoutOrderAndReusesItsRedirectUrl() {
        expectToken("tok-1", clock.instant().plusSeconds(3600));
        server.expect(requestTo(BASE + PineLabsPaymentGateway.CHECKOUT_PATH))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer tok-1"))
                .andExpect(header("Request-ID", NOT_BLANK))
                .andExpect(jsonPath("$.merchant_order_reference").value(org.hamcrest.Matchers.startsWith("DI42-")))
                .andExpect(jsonPath("$.order_amount.value").value(50000))
                .andExpect(jsonPath("$.order_amount.currency").value("INR"))
                .andExpect(jsonPath("$.pre_auth").value(false))
                .andExpect(jsonPath("$.integration_mode").value("REDIRECT"))
                .andExpect(jsonPath("$.callback_url").value("https://dinein.test/api/public/payments/pinelabs/callback?dinein_order=42"))
                .andExpect(jsonPath("$.purchase_details.customer.mobile_number").value("9876543210"))
                .andExpect(jsonPath("$.purchase_details.merchant_metadata.order_id").value("42"))
                .andRespond(withSuccess("{\"token\":\"t\",\"order_id\":\"" + ORDER_ID
                        + "\",\"redirect_url\":\"https://checkout.pl.test/redirect?token=abc\",\"response_code\":200}",
                        MediaType.APPLICATION_JSON));

        String orderId = gateway.createProviderOrder(ctx(null));
        CheckoutPayload payload = gateway.checkoutPayload(ctx(orderId));
        CheckoutPayload again = gateway.checkoutPayload(ctx(orderId));

        assertThat(orderId).isEqualTo(ORDER_ID);
        assertThat(payload.mode()).isEqualTo(CheckoutMode.REDIRECT);
        assertThat(payload.data()).isEqualTo(Map.of("url", "https://checkout.pl.test/redirect?token=abc"));
        assertThat(again.data()).isEqualTo(payload.data());
        server.verify();
    }

    @Test
    void recoversRedirectUrlAfterRestartViaIdempotentReference() {
        expectToken("tok-1", clock.instant().plusSeconds(3600));
        expectOrderFetch("tok-1", orderJson("CREATED", "PENDING"));
        server.expect(requestTo(BASE + PineLabsPaymentGateway.CHECKOUT_PATH))
                .andExpect(jsonPath("$.merchant_order_reference").value("DI42-ref"))
                .andRespond(withSuccess("{\"order_id\":\"" + ORDER_ID + "\",\"redirect_url\":\"https://checkout.pl.test/r2\"}",
                        MediaType.APPLICATION_JSON));

        assertThat(gateway.checkoutPayload(ctx(ORDER_ID)).data()).containsEntry("url", "https://checkout.pl.test/r2");
        server.verify();
    }

    @Test
    void cachesTokenUntilShortlyBeforeExpiry() {
        expectToken("tok-1", clock.instant().plusSeconds(3600));
        expectOrderFetch("tok-1", orderJson("PENDING", "PENDING"));
        expectOrderFetch("tok-1", orderJson("PENDING", "PENDING"));
        expectToken("tok-2", clock.instant().plusSeconds(7200));
        expectOrderFetch("tok-2", orderJson("PENDING", "PENDING"));

        gateway.fetchOrderPayments(ORDER_ID);
        clock.advance(Duration.ofMinutes(58));
        gateway.fetchOrderPayments(ORDER_ID);
        clock.advance(Duration.ofSeconds(90)); // inside the 60 s refresh window
        gateway.fetchOrderPayments(ORDER_ID);
        server.verify();
    }

    @Test
    void refreshesTokenOnceWhenRejectedAsUnauthorized() {
        expectToken("tok-1", clock.instant().plusSeconds(3600));
        server.expect(requestTo(BASE + PineLabsPaymentGateway.ORDER_PATH + ORDER_ID))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        expectToken("tok-2", clock.instant().plusSeconds(3600));
        expectOrderFetch("tok-2", orderJson("PROCESSED", "PROCESSED"));

        assertThat(gateway.fetchPayment(ORDER_ID, null).isCaptured()).isTrue();
        server.verify();
    }

    @Test
    void fetchPaymentMapsStatusAndAmount() {
        expectToken("tok-1", clock.instant().plusSeconds(3600));
        expectOrderFetch("tok-1", orderJson("PROCESSED", "PROCESSED"));

        ProviderPayment p = gateway.fetchPayment(ORDER_ID, null);

        assertThat(p).isEqualTo(new ProviderPayment(ORDER_ID, ORDER_ID + "-cc-1", ProviderPayment.Outcome.CAPTURED,
                50000, "upi", null));
        server.verify();
    }

    @Test
    void providerErrorsBecome502() {
        expectToken("tok-1", clock.instant().plusSeconds(3600));
        server.expect(requestTo(BASE + PineLabsPaymentGateway.ORDER_PATH + ORDER_ID))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body("{\"code\":\"INTERNAL\"}"));

        assertThatThrownBy(() -> gateway.fetchOrderPayments(ORDER_ID))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(e.getCode()).isEqualTo("PAYMENT_PROVIDER_ERROR");
                });
    }

    @Test
    void refundsAgainstTheChargedOrder() {
        expectToken("tok-1", clock.instant().plusSeconds(3600));
        server.expect(requestTo(BASE + PineLabsPaymentGateway.REFUND_PATH + ORDER_ID))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.merchant_order_reference").value(org.hamcrest.Matchers.startsWith("refund-42-")))
                .andExpect(jsonPath("$.order_amount.value").value(50000))
                .andExpect(jsonPath("$.order_amount.currency").value("INR"))
                .andRespond(withSuccess("{\"data\":{\"order_id\":\"v1-refund-1\",\"parent_order_id\":\"" + ORDER_ID
                        + "\",\"type\":\"REFUND\",\"status\":\"PROCESSED\"}}", MediaType.APPLICATION_JSON));

        ProviderRefund refund = gateway.refund(ORDER_ID, ORDER_ID + "-cc-1", 50000, "refund-42");

        assertThat(refund).isEqualTo(new ProviderRefund("v1-refund-1", true));
        server.verify();
    }

    @Test
    void failedRefundIsReported() {
        expectToken("tok-1", clock.instant().plusSeconds(3600));
        server.expect(requestTo(BASE + PineLabsPaymentGateway.REFUND_PATH + ORDER_ID))
                .andRespond(withSuccess("{\"data\":{\"order_id\":\"v1-refund-1\",\"status\":\"FAILED\"}}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> gateway.refund(ORDER_ID, null, 50000, "refund-42"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("REFUND_FAILED"));
    }

    @Test
    void callbackOnlyYieldsTheOrderIdAndRejectsGarbage() {
        assertThat(gateway.verifyClientCallback(Map.of("order_id", ORDER_ID, "status", "PROCESSED")).providerOrderId())
                .isEqualTo(ORDER_ID);
        assertThat(gateway.verifyClientCallback(Map.of("order_id", ORDER_ID)).providerPaymentId()).isNull();
        assertThatThrownBy(() -> gateway.verifyClientCallback(Map.of("status", "PROCESSED")))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("INVALID_SIGNATURE"));
        assertThatThrownBy(() -> gateway.verifyClientCallback(Map.of("order_id", "../../x")))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void callbackWithoutPineLabsOrderIdFallsBackToOurOrderIdFromTheCallbackUrl() {
        var verified = gateway.verifyClientCallback(Map.of(PineLabsPaymentGateway.ORDER_PARAM, "42"));

        assertThat(verified.providerOrderId()).isNull();
        assertThat(verified.internalOrderId()).isEqualTo(42L);
        assertThatThrownBy(() -> gateway.verifyClientCallback(Map.of(PineLabsPaymentGateway.ORDER_PARAM, "42; drop")))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void acceptsCamelCaseCreateResponse() {
        expectToken("tok-1", clock.instant().plusSeconds(3600));
        server.expect(requestTo(BASE + PineLabsPaymentGateway.CHECKOUT_PATH))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"orderId\":\"" + ORDER_ID + "\",\"redirectUrl\":\"https://pay.test/r/1\"}",
                        MediaType.APPLICATION_JSON));

        assertThat(gateway.createProviderOrder(ctx(null))).isEqualTo(ORDER_ID);
        assertThat(gateway.checkoutPayload(ctx(ORDER_ID)).data()).containsEntry("url", "https://pay.test/r/1");
        server.verify();
    }

    @Test
    void capturedPaymentWithoutPaymentAmountUsesTheOrderAmountAndCamelCaseMethod() {
        expectToken("tok-1", clock.instant().plusSeconds(3600));
        expectOrderFetch("tok-1", "{\"data\":{\"order_id\":\"" + ORDER_ID + "\",\"status\":\"PROCESSED\",\"amount\":50000,"
                + "\"payments\":[{\"id\":\"pay-1\",\"status\":\"PROCESSED\",\"paymentMethod\":\"UPI\"}]}}");

        ProviderPayment p = gateway.fetchPayment(ORDER_ID, null);

        assertThat(p).isEqualTo(new ProviderPayment(ORDER_ID, "pay-1", ProviderPayment.Outcome.CAPTURED, 50000, "upi", null));
        server.verify();
    }

    private Map<String, String> signedHeaders(String id, byte[] body) {
        String ts = String.valueOf(clock.instant().getEpochSecond());
        return Map.of("webhook-id", id, "webhook-timestamp", ts,
                "webhook-signature", PineLabsSignaturesTest.header(id, ts, body, SECRET));
    }

    @Test
    void webhookOrderProcessedBecomesCapturedEvent() {
        byte[] body = ("{\"event_type\":\"ORDER_PROCESSED\"," + orderJson("PROCESSED", "PROCESSED").trim().substring(1))
                .getBytes(StandardCharsets.UTF_8);

        WebhookParseResult result = gateway.parseWebhook(body, signedHeaders("evt_1", body));

        assertThat(result.signatureValid()).isTrue();
        assertThat(result.eventId()).isEqualTo("evt_1");
        assertThat(result.eventType()).isEqualTo("ORDER_PROCESSED");
        assertThat(result.events()).singleElement().satisfies(e -> {
            assertThat(e.type()).isEqualTo(GatewayEvent.Type.PAYMENT_CAPTURED);
            assertThat(e.payment().providerOrderId()).isEqualTo(ORDER_ID);
            assertThat(e.payment().amountPaise()).isEqualTo(50000);
        });
    }

    @Test
    void webhookPaymentFailedBecomesFailedEvent() {
        byte[] body = ("{\"event_type\":\"PAYMENT_FAILED\"," + orderJson("ATTEMPTED", "FAILED").trim().substring(1))
                .getBytes(StandardCharsets.UTF_8);

        WebhookParseResult result = gateway.parseWebhook(body, signedHeaders("evt_2", body));

        assertThat(result.events()).singleElement()
                .extracting(GatewayEvent::type).isEqualTo(GatewayEvent.Type.PAYMENT_FAILED);
    }

    @Test
    void webhookWithBadSignatureIsInvalidAndNotParsedIntoEvents() {
        byte[] body = ("{\"event_type\":\"ORDER_PROCESSED\"," + orderJson("PROCESSED", "PROCESSED").trim().substring(1))
                .getBytes(StandardCharsets.UTF_8);
        Map<String, String> headers = new java.util.HashMap<>(signedHeaders("evt_1", body));
        headers.put("webhook-id", "evt_forged");

        WebhookParseResult result = gateway.parseWebhook(body, headers);

        assertThat(result.signatureValid()).isFalse();
        assertThat(result.eventId()).isNull();
        assertThat(result.events()).isEmpty();
    }

    @Test
    void webhookRefundResolvesTheChargedPaymentFromTheParentOrder() {
        byte[] body = ("{\"event_type\":\"REFUND_PROCESSED\",\"data\":{\"order_id\":\"v1-refund-1\",\"parent_order_id\":\""
                + ORDER_ID + "\",\"type\":\"REFUND\",\"status\":\"PROCESSED\",\"payments\":[{\"id\":\"v1-refund-1-cc-1\","
                + "\"status\":\"PROCESSED\"}]}}").getBytes(StandardCharsets.UTF_8);
        expectToken("tok-1", clock.instant().plusSeconds(3600));
        expectOrderFetch("tok-1", orderJson("FULLY_REFUNDED", "PROCESSED"));

        WebhookParseResult result = gateway.parseWebhook(body, signedHeaders("evt_3", body));

        assertThat(result.events()).singleElement().satisfies(e -> {
            assertThat(e.type()).isEqualTo(GatewayEvent.Type.REFUND_PROCESSED);
            assertThat(e.providerPaymentId()).isEqualTo(ORDER_ID + "-cc-1");
            assertThat(e.refundId()).isEqualTo("v1-refund-1");
        });
        server.verify();
    }

    @Test
    void unconfiguredGatewayRefusesCalls() {
        PineLabsPaymentGateway unconfigured = new PineLabsPaymentGateway(
                new PineLabsProperties(null, "", null, null, null, Duration.ofMinutes(5)), objectMapper,
                RestClient.builder().build(), clock);

        assertThat(unconfigured.isConfigured()).isFalse();
        assertThatThrownBy(() -> unconfigured.fetchOrderPayments(ORDER_ID))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("PAYMENTS_NOT_CONFIGURED"));
    }

    static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
