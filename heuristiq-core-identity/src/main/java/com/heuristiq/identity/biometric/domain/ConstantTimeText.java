package com.heuristiq.identity.biometric.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Comparison of identifiers presented by a caller in time that does not depend on where they first differ. */
public final class ConstantTimeText {

    private ConstantTimeText() {
    }

    /** True when both are non-null and equal; the comparison time does not reveal the matching prefix. */
    public static boolean equal(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
