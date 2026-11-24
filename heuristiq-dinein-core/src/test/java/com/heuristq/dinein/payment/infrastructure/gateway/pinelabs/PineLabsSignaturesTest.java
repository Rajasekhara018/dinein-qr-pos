package com.heuristq.dinein.payment.infrastructure.gateway.pinelabs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heuristq.dinein.payment.infrastructure.gateway.ProviderPayment;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PineLabsSignaturesTest {

    private static final String SECRET = Base64.getEncoder().encodeToString("test-webhook-secret-32-bytes-long".getBytes(StandardCharsets.UTF_8));
    private static final Duration TOLERANCE = Duration.ofMinutes(5);
    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");
    private static final String TS = String.valueOf(NOW.getEpochSecond());
    private static final byte[] BODY = "{\"event_type\":\"ORDER_PROCESSED\",\"data\":{}}".getBytes(StandardCharsets.UTF_8);

    static String header(String id, String ts, byte[] body, String secret) {
        return "v1," + Base64.getEncoder().encodeToString(PineLabsSignatures.sign(id, ts, body, secret));
    }

    @Test
    void acceptsValidSignatureOverIdTimestampAndRawBody() {
        String sig = header("msg_1", TS, BODY, SECRET);

        assertThat(PineLabsSignatures.verifyWebhook(BODY, "msg_1", TS, sig, SECRET, NOW, TOLERANCE)).isTrue();
    }

    @Test
    void signatureUsesBase64DecodedSecretPerDocs() throws Exception {
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(Base64.getDecoder().decode(SECRET), "HmacSHA256"));
        byte[] expected = mac.doFinal(("msg_1." + TS + "." + new String(BODY, StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8));

        assertThat(PineLabsSignatures.sign("msg_1", TS, BODY, SECRET)).isEqualTo(expected);
        assertThat(PineLabsSignatures.sign("msg_1", TS, BODY, "whsec_" + SECRET)).isEqualTo(expected);
    }

    @Test
    void rejectsTamperedBodyIdOrWrongSecret() {
        String sig = header("msg_1", TS, BODY, SECRET);
        byte[] tampered = "{\"event_type\":\"ORDER_PROCESSED\",\"data\":{\"x\":1}}".getBytes(StandardCharsets.UTF_8);

        assertThat(PineLabsSignatures.verifyWebhook(tampered, "msg_1", TS, sig, SECRET, NOW, TOLERANCE)).isFalse();
        assertThat(PineLabsSignatures.verifyWebhook(BODY, "msg_2", TS, sig, SECRET, NOW, TOLERANCE)).isFalse();
        String other = Base64.getEncoder().encodeToString("another-secret-another-secret-xx".getBytes(StandardCharsets.UTF_8));
        assertThat(PineLabsSignatures.verifyWebhook(BODY, "msg_1", TS, sig, other, NOW, TOLERANCE)).isFalse();
    }

    @Test
    void rejectsStaleOrFutureTimestamps() {
        String old = String.valueOf(NOW.minusSeconds(301).getEpochSecond());
        String future = String.valueOf(NOW.plusSeconds(301).getEpochSecond());

        assertThat(PineLabsSignatures.verifyWebhook(BODY, "m", old, header("m", old, BODY, SECRET), SECRET, NOW, TOLERANCE)).isFalse();
        assertThat(PineLabsSignatures.verifyWebhook(BODY, "m", future, header("m", future, BODY, SECRET), SECRET, NOW, TOLERANCE)).isFalse();
        assertThat(PineLabsSignatures.verifyWebhook(BODY, "m", "abc", header("m", "abc", BODY, SECRET), SECRET, NOW, TOLERANCE)).isFalse();
    }

    @Test
    void acceptsAnyOfSeveralSignaturesAndIgnoresOtherVersions() {
        String good = header("m", TS, BODY, SECRET);
        String bad = "v1," + Base64.getEncoder().encodeToString(new byte[32]);

        assertThat(PineLabsSignatures.verifyWebhook(BODY, "m", TS, bad + " " + good, SECRET, NOW, TOLERANCE)).isTrue();
        assertThat(PineLabsSignatures.verifyWebhook(BODY, "m", TS, good.replace("v1,", "v2,"), SECRET, NOW, TOLERANCE)).isFalse();
        assertThat(PineLabsSignatures.verifyWebhook(BODY, "m", TS, "garbage", SECRET, NOW, TOLERANCE)).isFalse();
    }

    @Test
    void missingSecretOrHeadersIsInvalid() {
        String sig = header("m", TS, BODY, SECRET);

        assertThat(PineLabsSignatures.verifyWebhook(BODY, "m", TS, sig, null, NOW, TOLERANCE)).isFalse();
        assertThat(PineLabsSignatures.verifyWebhook(BODY, "m", TS, sig, "", NOW, TOLERANCE)).isFalse();
        assertThat(PineLabsSignatures.verifyWebhook(BODY, null, TS, sig, SECRET, NOW, TOLERANCE)).isFalse();
        assertThat(PineLabsSignatures.verifyWebhook(BODY, "m", null, sig, SECRET, NOW, TOLERANCE)).isFalse();
        assertThat(PineLabsSignatures.verifyWebhook(BODY, "m", TS, null, SECRET, NOW, TOLERANCE)).isFalse();
    }

    @Test
    void mapsPaymentStatusesToNeutralOutcomes() {
        assertThat(PineLabsPaymentGateway.outcome("PROCESSED")).isEqualTo(ProviderPayment.Outcome.CAPTURED);
        assertThat(PineLabsPaymentGateway.outcome("AUTHORIZED")).isEqualTo(ProviderPayment.Outcome.AUTHORIZED);
        assertThat(PineLabsPaymentGateway.outcome("FAILED")).isEqualTo(ProviderPayment.Outcome.FAILED);
        assertThat(PineLabsPaymentGateway.outcome("CANCELLED")).isEqualTo(ProviderPayment.Outcome.FAILED);
        assertThat(PineLabsPaymentGateway.outcome("PENDING")).isEqualTo(ProviderPayment.Outcome.PENDING);
        assertThat(PineLabsPaymentGateway.outcome(null)).isEqualTo(ProviderPayment.Outcome.PENDING);
    }

    @Test
    void mapsOrderPaymentsAndSynthesisesFailureForClosedOrders() throws Exception {
        ObjectMapper om = new ObjectMapper();
        List<ProviderPayment> payments = PineLabsPaymentGateway.toPayments(om.readTree("""
                {"order_id":"v1-1-aa-X","status":"PROCESSED","payments":[
                  {"id":"v1-1-aa-X-cc-1","status":"FAILED","payment_amount":{"value":50000},"payment_method":"CARD",
                   "error_detail":{"code":"PAYMENT_DECLINED","message":"Declined"}},
                  {"id":"v1-1-aa-X-cc-2","status":"PROCESSED","payment_amount":{"value":50000},"payment_method":"UPI"}]}
                """));

        assertThat(payments).extracting(ProviderPayment::outcome)
                .containsExactly(ProviderPayment.Outcome.FAILED, ProviderPayment.Outcome.CAPTURED);
        assertThat(payments.get(0).errorDescription()).isEqualTo("Declined");
        assertThat(payments.get(1)).isEqualTo(new ProviderPayment("v1-1-aa-X", "v1-1-aa-X-cc-2",
                ProviderPayment.Outcome.CAPTURED, 50000, "upi", null));
        assertThat(PineLabsPaymentGateway.mostRelevant(payments)).get()
                .extracting(ProviderPayment::providerPaymentId).isEqualTo("v1-1-aa-X-cc-2");

        List<ProviderPayment> closed = PineLabsPaymentGateway.toPayments(om.readTree(
                "{\"order_id\":\"v1-2\",\"status\":\"CANCELLED\",\"payments\":[]}"));
        assertThat(closed).singleElement().extracting(ProviderPayment::outcome).isEqualTo(ProviderPayment.Outcome.FAILED);
    }
}
