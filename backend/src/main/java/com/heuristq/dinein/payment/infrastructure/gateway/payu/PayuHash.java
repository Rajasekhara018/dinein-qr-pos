package com.heuristq.dinein.payment.infrastructure.gateway.payu;

import com.heuristq.dinein.shared.util.SecureTokens;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;

/** PayU SHA-512 hash formulas (request, response/reverse, and postservice API commands). */
public final class PayuHash {

    private PayuHash() {
    }

    /** {@code sha512(key|txnid|amount|productinfo|firstname|email|udf1|udf2|udf3|udf4|udf5||||||salt)} */
    public static String request(String key, String salt, Map<String, String> f) {
        return sha512(key + "|" + v(f, "txnid") + "|" + v(f, "amount") + "|" + v(f, "productinfo") + "|"
                + v(f, "firstname") + "|" + v(f, "email") + "|" + v(f, "udf1") + "|" + v(f, "udf2") + "|"
                + v(f, "udf3") + "|" + v(f, "udf4") + "|" + v(f, "udf5") + "||||||" + salt);
    }

    /**
     * Response hash:
     * {@code sha512([additionalCharges|]salt|status||||||udf5|udf4|udf3|udf2|udf1|email|firstname|productinfo|amount|txnid|key)}
     */
    public static boolean verifyResponse(String key, String salt, Map<String, String> f) {
        String received = f.get("hash");
        if (received == null || received.isBlank()) {
            return false;
        }
        String base = salt + "|" + v(f, "status") + "||||||" + v(f, "udf5") + "|" + v(f, "udf4") + "|"
                + v(f, "udf3") + "|" + v(f, "udf2") + "|" + v(f, "udf1") + "|" + v(f, "email") + "|"
                + v(f, "firstname") + "|" + v(f, "productinfo") + "|" + v(f, "amount") + "|" + v(f, "txnid") + "|" + key;
        String charges = v(f, "additionalCharges");
        String expected = sha512(charges.isEmpty() ? base : charges + "|" + base);
        return SecureTokens.constantTimeEquals(expected, received.trim().toLowerCase(Locale.ROOT));
    }

    /** {@code sha512(key|command|var1|salt)} for postservice API calls. */
    public static String command(String key, String command, String var1, String salt) {
        return sha512(key + "|" + command + "|" + var1 + "|" + salt);
    }

    static String sha512(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-512").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String v(Map<String, String> f, String k) {
        String value = f.get(k);
        return value == null ? "" : value;
    }
}
