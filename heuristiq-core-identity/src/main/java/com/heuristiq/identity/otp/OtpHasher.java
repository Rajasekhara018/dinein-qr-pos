package com.heuristiq.uam.otp;

import com.heuristiq.uam.common.UamCommonConstants;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/** Salted SHA-256 hashing and constant-time comparison for OTPs held at rest. */
public final class OtpHasher {
    private static final SecureRandom RANDOM = new SecureRandom();

    private OtpHasher() {
    }

    /** Returns a fresh random salt, Base64 encoded. */
    public static String newSalt() {
        byte[] salt = new byte[OtpConstants.SALT_BYTES];
        RANDOM.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    /** Hashes {@code otp} bound to its challenge {@code key} and {@code salt}; a null otp hashes as empty. */
    public static String hash(String key, String salt, String otp) {
        try {
            MessageDigest digest = MessageDigest.getInstance(UamCommonConstants.SHA_256);
            digest.update(salt.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(key.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update((otp == null ? "" : otp).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    /** Constant-time equality of two stored/derived hashes. */
    public static boolean matches(String expectedHash, String actualHash) {
        return MessageDigest.isEqual(
                expectedHash.getBytes(StandardCharsets.UTF_8), actualHash.getBytes(StandardCharsets.UTF_8));
    }
}
