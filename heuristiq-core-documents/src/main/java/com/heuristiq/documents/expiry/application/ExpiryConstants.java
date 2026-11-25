package com.heuristiq.documents.expiry.application;

import java.time.Duration;

/** Fixed values of document expiry (D12): messages and the job's lock. */
public final class ExpiryConstants {

    public static final String TENANT_REQUIRED = "tenantId is required";
    public static final String OWNERS_REQUIRED = "ownerIds must name between 1 and ";

    /** The reminder job runs on one replica at a time. */
    public static final String REMINDER_LOCK_KEY = "dms-expiry-reminders";
    public static final Duration REMINDER_LOCK_LEASE = Duration.ofMinutes(15);

    private ExpiryConstants() {
    }
}
