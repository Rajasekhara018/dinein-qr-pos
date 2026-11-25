package com.heuristiq.identity.refreshToken;

import com.heuristiq.identity.common.UamCommonConstants;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * How a raw refresh token is stored: only its SHA-256 hex digest is ever persisted, and digests are compared in
 * constant time.
 */
public final class RefreshTokenHashing {

    private RefreshTokenHashing() {
    }

    /** SHA-256 hex digest of {@code raw}. */
    public static String sha256Hex(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance(UamCommonConstants.SHA_256);
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /** Constant-time comparison of two stored digests; false when either is null. */
    public static boolean sameHash(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
