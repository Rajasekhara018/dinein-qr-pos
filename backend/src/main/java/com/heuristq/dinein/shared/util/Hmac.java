package com.heuristq.dinein.shared.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

public final class Hmac {

    private Hmac() {
    }

    public static byte[] sha256(byte[] data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }

    public static String sha256Hex(byte[] data, String secret) {
        return HexFormat.of().formatHex(sha256(data, secret));
    }

    public static String sha256Hex(String data, String secret) {
        return sha256Hex(data.getBytes(StandardCharsets.UTF_8), secret);
    }
}
