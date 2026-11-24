package com.railcab.audit.application;

/** Fixed values used when reading audit events back (search, detail, correlation, payload). */
public final class QueryConstants {

    private QueryConstants() {
    }

    /** Most events returned for one correlation id. */
    public static final int CORRELATION_MAX = 500;

    /** Message when an event does not exist or is not visible to the caller. */
    public static final String EVENT_NOT_FOUND = "Audit event not found";

    /** Message when an event exists but no payload was stored for it. */
    public static final String PAYLOAD_NOT_FOUND = "No payload was stored for this event";
}
