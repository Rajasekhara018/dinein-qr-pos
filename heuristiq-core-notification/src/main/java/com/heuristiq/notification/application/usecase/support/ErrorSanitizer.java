package com.railcab.nms.application.usecase.support;

import java.util.regex.Pattern;

/**
 * Makes a provider or exception message safe to store and to forward to the audit service: strips
 * credential-looking {@code key=value} pairs (Digimile puts its password in the request URL, and
 * client exceptions echo the URL), query strings, bearer tokens and control characters, then cuts
 * the text to a bounded length.
 */
public final class ErrorSanitizer {

    /** Longest reason kept on an attempt record. */
    public static final int MAX_LENGTH = 500;

    private static final Pattern SECRET_PAIR = Pattern.compile(
            "(?i)\\b(password|passwd|pwd|pass|token|secret|api[_-]?key|apikey|auth|authorization|signature|otp|username|user)"
                    + "\\s*[=:]\\s*[^&\\s,;\"']+");
    private static final Pattern QUERY_STRING = Pattern.compile("\\?[^\\s\"']*");
    private static final Pattern BEARER = Pattern.compile("(?i)bearer\\s+[A-Za-z0-9._~+/=-]+");
    private static final Pattern CONTROL = Pattern.compile("[\\p{Cntrl}&&[^\\t]]");

    private ErrorSanitizer() {
    }

    /** Returns a safe, bounded version of {@code raw}, or {@code null} when there is nothing to keep. */
    public static String sanitize(String raw) {
        return sanitize(raw, MAX_LENGTH);
    }

    public static String sanitize(String raw, int maxLength) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String cleaned = BEARER.matcher(raw).replaceAll("Bearer ***");
        cleaned = SECRET_PAIR.matcher(cleaned).replaceAll("$1=***");
        cleaned = QUERY_STRING.matcher(cleaned).replaceAll("?***");
        cleaned = CONTROL.matcher(cleaned).replaceAll(" ").trim();
        return cleaned.length() <= maxLength ? cleaned : cleaned.substring(0, maxLength);
    }
}
