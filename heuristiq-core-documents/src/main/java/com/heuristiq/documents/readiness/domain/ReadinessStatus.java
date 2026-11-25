package com.heuristiq.documents.readiness.domain;

import java.util.Locale;

/**
 * Outcome of one readiness check (docs/APPLICATION_READINESS.md). A service reports facts only: severity, phase and
 * order are decided by Rider_UAM's readiness profile.
 */
public enum ReadinessStatus {
    /** The configuration step is done. */
    PASS,
    /** The configuration step is missing. */
    FAIL,
    /** Done, but with something worth a look. */
    WARN,
    /** Could not be evaluated (a dependency down, a timeout, or no way to look it up). */
    UNKNOWN,
    /** The application does not use this service or this check. */
    NOT_APPLICABLE;

    /** The last segment of the check's message key: the status in lower case, e.g. {@code not_applicable}. */
    public String messageKeySuffix() {
        return name().toLowerCase(Locale.ROOT);
    }
}
