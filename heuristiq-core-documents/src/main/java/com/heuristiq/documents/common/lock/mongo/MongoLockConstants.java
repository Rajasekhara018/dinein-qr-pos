package com.railcab.dms.common.lock.mongo;

/** Fixed values of the Mongo job-lock adapter: the collection and the field names it queries and updates. */
public final class MongoLockConstants {

    private MongoLockConstants() {
    }

    /** Collection holding one lease document per job key. */
    public static final String LOCK_COLLECTION = "dms_job_locks";
    public static final String FIELD_ID = "_id";
    public static final String FIELD_LOCKED_UNTIL = "lockedUntil";
    public static final String FIELD_OWNER = "owner";
}
