package com.heuristiq.identity.readiness.domain;

/** How much a failing step matters, decided by Rider_UAM's readiness profile (never by the service reporting it). */
public enum ReadinessSeverity {
    /** The application must not go live while this step is not passed. */
    BLOCK,
    /** Recommended; a failure is shown as a warning but does not stop go-live. */
    WARN
}
