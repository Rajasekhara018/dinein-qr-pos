package com.heuristiq.gateway.auth.util;

import org.springframework.util.StringUtils;

import java.security.MessageDigest;
import java.util.Arrays;

public final class SecurityUtils {

    private SecurityUtils() {}

    /**
     * Validates the provided internal key against a comma-separated list of allowed keys.
     * Uses constant-time comparison to prevent timing-based enumeration of valid keys.
     */
    public static boolean validateInternalTokenKey(String providedKey, String configuredKeys) {
        if (!StringUtils.hasText(providedKey) || !StringUtils.hasText(configuredKeys)) {
            return false;
        }
        byte[] provided = providedKey.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return Arrays.stream(configuredKeys.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .anyMatch(key -> MessageDigest.isEqual(
                        key.getBytes(java.nio.charset.StandardCharsets.UTF_8), provided));
    }
}
