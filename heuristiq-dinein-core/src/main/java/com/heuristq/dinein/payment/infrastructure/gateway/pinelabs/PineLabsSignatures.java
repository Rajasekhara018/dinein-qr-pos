package com.heuristq.dinein.payment.infrastructure.gateway.pinelabs;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * Pine Labs webhook signatures (Standard Webhooks style): headers {@code webhook-id}, {@code webhook-timestamp}
 * (unix seconds) and {@code webhook-signature} = {@code v1,<base64(HMAC_SHA256(secret, id + "." + timestamp + "." +
 * rawBody))>}, where the dashboard secret is base64 and is decoded before use. Compared in constant time.
 */
public final class PineLabsSignatures {

    private static final String WHSEC_PREFIX = "whsec_";

    private PineLabsSignatures() {
    }

    public static boolean verifyWebhook(byte[] rawBody, String webhookId, String timestamp, String signatureHeader,
                                        String secret, Instant now, Duration tolerance) {
        if (rawBody == null || isBlank(webhookId) || isBlank(timestamp) || isBlank(signatureHeader) || isBlank(secret)) {
            return false;
        }
        long ts;
        try {
            ts = Long.parseLong(timestamp.trim());
        } catch (NumberFormatException e) {
            return false;
        }
        // Rejects replays of old (validly signed) deliveries and clocks that are far off.
        if (Math.abs(now.getEpochSecond() - ts) > tolerance.toSeconds()) {
            return false;
        }
        byte[] expected = sign(webhookId.trim(), timestamp.trim(), rawBody, secret);
        // The header may carry several space-separated signatures (key rotation), each "v1,<base64>".
        for (String candidate : signatureHeader.trim().split(" +")) {
            int comma = candidate.indexOf(',');
            if (comma < 0 || !"v1".equals(candidate.substring(0, comma))) {
                continue;
            }
            byte[] received;
            try {
                received = Base64.getDecoder().decode(candidate.substring(comma + 1));
            } catch (IllegalArgumentException e) {
                continue;
            }
            if (MessageDigest.isEqual(expected, received)) {
                return true;
            }
        }
        return false;
    }

    /** Raw HMAC over {@code id.timestamp.body}; the header value is {@code "v1," + base64(result)}. */
    public static byte[] sign(String webhookId, String timestamp, byte[] rawBody, String secret) {
        byte[] prefix = (webhookId + "." + timestamp + ".").getBytes(StandardCharsets.UTF_8);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretKey(secret), "HmacSHA256"));
            mac.update(prefix);
            return mac.doFinal(rawBody);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }

    /**
     * The docs say the dashboard secret is base64 and must be decoded. A {@code whsec_} prefix (Standard Webhooks
     * convention) is stripped. VERIFY: if the dashboard ever shows a non-base64 secret, the raw UTF-8 bytes are used.
     */
    static byte[] secretKey(String secret) {
        String s = secret.trim();
        if (s.startsWith(WHSEC_PREFIX)) {
            s = s.substring(WHSEC_PREFIX.length());
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(s);
            if (decoded.length > 0) {
                return decoded;
            }
        } catch (IllegalArgumentException ignored) {
            // not base64: fall through
        }
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
