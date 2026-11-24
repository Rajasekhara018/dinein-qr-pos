package com.heuristiq.paymentservice.payment.gateway.razorpay;

import com.heuristiq.paymentservice.config.RazorpayProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Verifies Razorpay's X-Razorpay-Signature (HMAC-SHA256 of the raw webhook body against
 * app.razorpay.webhook-secret) and computes a stable dedupe digest for webhook bodies that
 * arrive with no provider event-id header. Extracted out of PaymentService (which owns the
 * ride-payment webhook) so WalletTopupService's separate wallet-topup webhook can reuse the
 * exact same, security-critical verification logic rather than re-implementing it.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RazorpayWebhookSignatureVerifier {

    private final RazorpayProperties razorpayProperties;

    public boolean verify(String rawBody, String signature) {
        if (signature == null || signature.isBlank()) {
            return false;
        }
        String secret = razorpayProperties.getWebhookSecret();
        if (secret == null || secret.isBlank()) {
            log.warn("app.razorpay.webhook-secret is not configured — rejecting webhook");
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
            String generated = HexFormat.of().formatHex(hash);
            return MessageDigest.isEqual(generated.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("Webhook signature verification error: {}", e.getMessage());
            return false;
        }
    }

    /** Razorpay's webhook payload shape: {payload: {payment: {entity: {...}}}}. Shared between
     *  PaymentService's ride-payment webhook and WalletTopupService's wallet-topup webhook. */
    public JSONObject extractPaymentEntity(JSONObject payload) {
        try {
            return payload.getJSONObject("payload").getJSONObject("payment").getJSONObject("entity");
        } catch (Exception e) {
            return null;
        }
    }

    public String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
