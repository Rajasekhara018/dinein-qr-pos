package com.heuristiq.paymentservice.payment.gateway.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * SHA-512 hex digest helper for PayU's/Toucan's hash-chain checkout/refund/status field
 * ("key|txnid|amount|productinfo|firstname|email|udf1..udf5||||||salt" for PayU checkout,
 * "key|command|var1|salt" for PayU's postservice.php commands). The formula itself is ported
 * faithfully from the reference design; no credential ever flows through this class.
 */
public final class Sha512Hasher {

    private Sha512Hasher() {
    }

    public static String hex(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-512");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-512 algorithm unavailable", e);
        }
    }
}
