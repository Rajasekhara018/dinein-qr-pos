package com.heuristq.dinein.payment.infrastructure.gateway.razorpay;

import com.heuristq.dinein.shared.util.Hmac;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class RazorpaySignaturesTest {

    private static final String SECRET = "test_key_secret";

    @Test
    void acceptsValidPaymentSignature() {
        String signature = Hmac.sha256Hex("order_ABC|pay_XYZ", SECRET);

        assertThat(RazorpaySignatures.verifyPayment("order_ABC", "pay_XYZ", signature, SECRET)).isTrue();
        assertThat(RazorpaySignatures.verifyPayment("order_ABC", "pay_XYZ", signature.toUpperCase(), SECRET)).isTrue();
    }

    @Test
    void rejectsTamperedOrForeignSignatures() {
        String signature = Hmac.sha256Hex("order_ABC|pay_XYZ", SECRET);

        assertThat(RazorpaySignatures.verifyPayment("order_ABC", "pay_OTHER", signature, SECRET)).isFalse();
        assertThat(RazorpaySignatures.verifyPayment("order_OTHER", "pay_XYZ", signature, SECRET)).isFalse();
        assertThat(RazorpaySignatures.verifyPayment("order_ABC", "pay_XYZ", signature, "wrong_secret")).isFalse();
        assertThat(RazorpaySignatures.verifyPayment("order_ABC", "pay_XYZ", "deadbeef", SECRET)).isFalse();
        assertThat(RazorpaySignatures.verifyPayment("order_ABC", "pay_XYZ", null, SECRET)).isFalse();
        assertThat(RazorpaySignatures.verifyPayment("order_ABC", "pay_XYZ", signature, "")).isFalse();
    }

    @Test
    void webhookSignatureIsComputedOverRawBytes() {
        byte[] body = "{\"event\":\"payment.captured\",\"payload\":{}}".getBytes(StandardCharsets.UTF_8);
        String signature = Hmac.sha256Hex(body, "whsec");

        assertThat(RazorpaySignatures.verifyWebhook(body, signature, "whsec")).isTrue();
        // Re-serialised JSON with different whitespace must not verify.
        byte[] reformatted = "{ \"event\": \"payment.captured\", \"payload\": {} }".getBytes(StandardCharsets.UTF_8);
        assertThat(RazorpaySignatures.verifyWebhook(reformatted, signature, "whsec")).isFalse();
        assertThat(RazorpaySignatures.verifyWebhook(body, signature, "other")).isFalse();
    }
}
