package com.heuristiq.identity.metrics;

/** Closed set of values for the {@code result} metric tag, so the tag can never grow without bound. */
public enum Outcome {
    SUCCESS("success"),
    /** The first step succeeded but a second factor is still required. */
    MFA_REQUIRED("mfa_required"),
    /** The caller was refused for a business reason (bad credentials, invalid token or OTP, duplicate...). */
    REJECTED("rejected"),
    /** An unexpected failure (a bug or an outage), not the caller's fault. */
    ERROR("error");

    private final String tag;

    Outcome(String tag) {
        this.tag = tag;
    }

    /** The lowercase tag value published to the metrics registry. */
    public String tag() {
        return tag;
    }
}
