package com.heuristiq.uam.readiness.domain;

import java.util.Locale;
import java.util.Optional;

/**
 * Result of one readiness check, as a service reports it (docs/APPLICATION_READINESS.md). {@link #UNKNOWN} means the check
 * could not be evaluated (a service down, a timeout); it is never treated as passed.
 */
public enum ReadinessStatus {
    PASS, FAIL, WARN, UNKNOWN, NOT_APPLICABLE;

    /** True for the statuses that let a later step go ahead: passed, passed with a remark, or not used by the application. */
    public boolean satisfied() {
        return this == PASS || this == WARN || this == NOT_APPLICABLE;
    }

    /** The suffix of a message key: the status in lower case ({@code not_applicable} for {@link #NOT_APPLICABLE}). */
    public String keySuffix() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The status for a wire value (trimmed, any case), or empty when it is not one of the five. */
    public static Optional<ReadinessStatus> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        for (ReadinessStatus status : values()) {
            if (status.name().equals(normalized)) {
                return Optional.of(status);
            }
        }
        return Optional.empty();
    }
}
