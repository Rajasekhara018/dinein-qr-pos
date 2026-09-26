package com.heuristq.dinein.payment.gateway.razorpay;

import com.heuristq.dinein.shared.util.Hmac;
import com.heuristq.dinein.shared.util.SecureTokens;

import java.util.Locale;

/** Razorpay HMAC-SHA256 signature checks, compared in constant time. */
public final class RazorpaySignatures {

    private RazorpaySignatures() {
    }

    /** Checkout success handler: {@code HMAC_SHA256(order_id + "|" + payment_id, key_secret)}. */
    public static boolean verifyPayment(String razorpayOrderId, String razorpayPaymentId, String signature,
                                        String keySecret) {
        if (isBlank(razorpayOrderId) || isBlank(razorpayPaymentId) || isBlank(signature) || isBlank(keySecret)) {
            return false;
        }
        String expected = Hmac.sha256Hex(razorpayOrderId + "|" + razorpayPaymentId, keySecret);
        return SecureTokens.constantTimeEquals(expected, signature.trim().toLowerCase(Locale.ROOT));
    }

    /** Webhooks: {@code HMAC_SHA256(raw request body bytes, webhook_secret)}; must use the unparsed body. */
    public static boolean verifyWebhook(byte[] rawBody, String signature, String webhookSecret) {
        if (rawBody == null || isBlank(signature) || isBlank(webhookSecret)) {
            return false;
        }
        String expected = Hmac.sha256Hex(rawBody, webhookSecret);
        return SecureTokens.constantTimeEquals(expected, signature.trim().toLowerCase(Locale.ROOT));
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
