package com.railcab.audit.adapter.out.persistence;

import org.springframework.data.domain.Sort;

/**
 * Fixed values shared by the MongoDB and JPA adapters (sort order, purge batch, duplicate-key code).
 */
public final class PersistenceConstants {

    private PersistenceConstants() {
    }

    public static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, PersistenceConstants.FIELD_OCCURRED_AT,
            PersistenceConstants.FIELD_ID);

    public static final int PURGE_BATCH = 5000;

    public static final int DUPLICATE_KEY = 11000;

    public static final String AUDIT_EVENTS_COLLECTION = "audit_events";
    public static final String PAYLOADS_COLLECTION = "audit_event_payloads";
    public static final String SEQUENCES_COLLECTION = "audit_sequences";
    public static final String LOCKS_COLLECTION = "scheduler_locks";
    /** Header names may contain dots, which are not allowed in Mongo field names; this look-alike is stored instead. */
    public static final String DOT_REPLACEMENT = "．";

    /* Stored field names used in queries by both the Mongo and the JPA adapter. */
    public static final String FIELD_ID = "id";
    /** Mongo primary key field. */
    public static final String FIELD_MONGO_ID = "_id";
    /** Counter field of a sequence document. */
    public static final String FIELD_SEQUENCE_VALUE = "value";
    public static final String FIELD_USER_ID = "userId";
    public static final String FIELD_ROLE = "role";
    public static final String FIELD_ROLES = "roles";
    public static final String FIELD_FEATURE = "feature";
    public static final String FIELD_SCREEN_KEY = "screenKey";
    public static final String FIELD_EVENT_TYPE = "eventType";
    public static final String FIELD_OUTCOME = "outcome";
    public static final String FIELD_APP_CHANNEL = "appChannel";
    public static final String FIELD_PRIVILEGE_IDS = "privilegeIds";
    public static final String FIELD_METHOD = "method";
    public static final String FIELD_PATH = "path";
    public static final String FIELD_CORRELATION_ID = "correlationId";
    public static final String FIELD_SUBJECT_TYPE = "subjectType";
    public static final String FIELD_SUBJECT_ID = "subjectId";
    public static final String FIELD_REASON_CODE = "reasonCode";
    public static final String FIELD_OCCURRED_AT = "occurredAt";
    public static final String FIELD_TENANT_ID = "tenantId";
    public static final String FIELD_EVENT_ID = "eventId";
    public static final String FIELD_EXPIRE_AT = "expireAt";

}
