package com.heuristiq.audit.adapter.out.lock;

import com.heuristiq.audit.domain.SchedulerLock;

/** SQL, field names and fixed values used by the {@link SchedulerLock} adapters. */
final class SchedulerLockConstants {

    private SchedulerLockConstants() {
    }

    /**
     * Atomic take-or-renew of a lease. The row is inserted when absent; when present it is overwritten only
     * if the previous lease has run out or is already ours, so exactly one caller gets an update count of 1.
     * Both timestamps come from the database clock, so instances with skewed clocks agree on expiry.
     * Parameters: name, holder, lease milliseconds.
     */
    static final String ACQUIRE_SQL = "INSERT INTO scheduler_locks (name, holder, locked_until) "
            + "VALUES (?, ?, now() + (? * interval '1 millisecond')) "
            + "ON CONFLICT (name) DO UPDATE SET holder = EXCLUDED.holder, locked_until = EXCLUDED.locked_until "
            + "WHERE scheduler_locks.locked_until < now() OR scheduler_locks.holder = EXCLUDED.holder";

    /** Gives a lease back early. Parameters: name, holder. */
    static final String RELEASE_SQL = "UPDATE scheduler_locks SET locked_until = now() - interval '1 second' "
            + "WHERE name = ? AND holder = ?";

    /** Holder id used when the host name can not be resolved. */
    static final String FALLBACK_HOLDER = "instance";

    /** Message of the error raised for a non-positive lease length. */
    static final String TTL_NOT_POSITIVE = "ttlMillis must be positive";

    /** Mongo lease document: id field. */
    static final String ID_FIELD = "_id";

    /** Mongo lease document: holder field. */
    static final String HOLDER_FIELD = "holder";

    /** Mongo lease document: expiry field. */
    static final String LOCKED_UNTIL_FIELD = "lockedUntil";

    /** How far into the past a released Mongo lease's expiry is set, in milliseconds. */
    static final long RELEASE_BACKDATE_MS = 1000;
}
