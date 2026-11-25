package com.heuristiq.identity.device;

import com.heuristiq.identity.common.UamCommonConstants;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * The identity of a device: a hash of its User-Agent header. Login creates (or finds) the device by
 * this value, and anything later that must act on "the device making this request" -- such as
 * recording its location -- finds it the same way, so the two always agree.
 */
public final class DeviceFingerprint {

    private DeviceFingerprint() {
    }

    public static String of(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return DeviceConstants.UNKNOWN_DEVICE;
        }
        try {
            byte[] hash = MessageDigest.getInstance(UamCommonConstants.SHA_256).digest(userAgent.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
