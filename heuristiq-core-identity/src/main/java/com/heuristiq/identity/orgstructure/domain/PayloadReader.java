package com.heuristiq.uam.orgstructure.domain;

import java.util.Map;

/** Reads typed values out of a draft change payload, failing with a clear message when one is missing or malformed. */
public final class PayloadReader {
    private PayloadReader() {
    }

    /** A required, non-blank value. */
    public static String required(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            throw OrgStructureException.invalid("Missing value: " + key);
        }
        return String.valueOf(value);
    }

    /** An optional value; null when absent or blank. */
    public static String optional(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return value == null || String.valueOf(value).isBlank() ? null : String.valueOf(value);
    }

    public static int requiredInt(Map<String, Object> payload, String key) {
        try {
            return Integer.parseInt(required(payload, key));
        } catch (NumberFormatException e) {
            throw OrgStructureException.invalid("Not a number: " + key);
        }
    }

    /** A boolean flag, defaulting when absent. */
    public static boolean flag(Map<String, Object> payload, String key, boolean defaultValue) {
        Object value = payload.get(key);
        return value == null ? defaultValue : Boolean.parseBoolean(String.valueOf(value));
    }
}
