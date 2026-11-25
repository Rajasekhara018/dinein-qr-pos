package com.heuristiq.identity.selfregister;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/** Builds and normalizes the keys the self-registration checks are stored under. Pure, so it is easy to test. */
final class SelfRegisterKeys {

    private SelfRegisterKeys() {
    }

    /** Lower-case and trimmed; the same address always gives the same key. */
    static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    static String normalizeApplicationCode(String applicationCode) {
        return applicationCode == null ? "" : applicationCode.trim().toUpperCase(Locale.ROOT);
    }

    /** The domain part of an address, or empty when there is none. */
    static String domainOf(String normalizedEmail) {
        int at = normalizedEmail.lastIndexOf('@');
        return at < 0 ? "" : normalizedEmail.substring(at + 1);
    }

    static String emailKey(String normalizedEmail, String applicationCode) {
        return SelfRegisterConstants.EMAIL_KEY_PREFIX + normalizedEmail + SelfRegisterConstants.KEY_SEPARATOR + applicationCode;
    }

    static String tokenKey(String normalizedEmail, String applicationCode) {
        return SelfRegisterConstants.TOKEN_KEY_PREFIX + normalizedEmail + SelfRegisterConstants.KEY_SEPARATOR + applicationCode;
    }

    /** The client address is hashed, so the stored key is not a record of who asked. */
    static String addressKey(String clientAddress) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((clientAddress == null ? "" : clientAddress).getBytes(StandardCharsets.UTF_8));
            return SelfRegisterConstants.ADDRESS_KEY_PREFIX + HexFormat.of().formatHex(digest, 0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
