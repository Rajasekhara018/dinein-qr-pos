package com.heuristiq.audit.application;

import java.util.Set;

/**
 * Fixed values of the retention classes and the purge lease.
 */
public final class RetentionConstants {

    private RetentionConstants() {
    }

    public static final Set<String> READ_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    /** Lease name of the purge job. */
    public static final String LOCK_NAME = "audit-retention";

    /** Lease length is at least this long, whatever the run interval. */
    public static final long MIN_LEASE_MS = 60_000L;

}
