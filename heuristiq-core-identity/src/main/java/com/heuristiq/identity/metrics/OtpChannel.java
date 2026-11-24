package com.heuristiq.uam.metrics;

/** Closed set of values for the {@code channel} metric tag of the OTP metrics. */
public enum OtpChannel {
    PHONE("phone"),
    EMAIL("email");

    private final String tag;

    OtpChannel(String tag) {
        this.tag = tag;
    }

    /** The lowercase tag value published to the metrics registry. */
    public String tag() {
        return tag;
    }
}
