package com.railcab.dms.centralaudit;

import com.railcab.dms.common.DmsCommonConstants;

import java.time.Duration;

/** Fixed values of the events DMS publishes to the central audit service. */
public final class CentralAuditConstants {

    private CentralAuditConstants() {
    }

    public static final String EVENT_TYPE = "DOMAIN";
    public static final String SOURCE_SERVICE = "rider-dms";
    public static final String FEATURE = "dms";
    public static final String MODULE = "DOCUMENTS";
    public static final String APP_CHANNEL = "SERVICE";
    public static final String SUBJECT_TYPE_DOCUMENT = "DOCUMENT";

    public static final String OUTCOME_SUCCESS = "SUCCESS";
    public static final String OUTCOME_FAILURE = "FAILURE";
    public static final String OUTCOME_DENIED = "DENIED";

    /** Event ids allow only A-Z a-z 0-9 _ - and 8-64 characters. */
    public static final String EVENT_ID_PREFIX = "dms-";

    // details keys (contract with Rider_AuditService)
    public static final String D_ACTION = "action";
    public static final String D_DOCUMENT_TYPE = "documentType";
    public static final String D_CATEGORY = "category";
    public static final String D_REASON_CODE = "reasonCode";
    public static final String D_STORAGE_PROVIDER = "storageProvider";
    public static final String D_SCAN_VERDICT = "scanVerdict";
    public static final String D_SIZE_BYTES = "sizeBytes";
    public static final String D_MESSAGE = "message";

    /** Wire constants of the audit service ingest API. */
    public static final String INGEST_PATH = "/api/v1/audit/internal/events";
    public static final String HEADER_SERVICE_TOKEN = DmsCommonConstants.SERVICE_TOKEN_HEADER;
    public static final String EVENTS_PREFIX = "{\"events\":[";
    public static final String EVENTS_SUFFIX = "]}";

    public static final int ERROR_MAX_LENGTH = 300;
    public static final int DETAIL_MAX_LENGTH = 250;

    /** Length of the outbox's last-error column; a longer error is cut to fit. */
    public static final int ERROR_COLUMN_LENGTH = 500;

    // ---- resilient client toward the audit service (wiring)
    public static final String AUDIT_DOWNSTREAM = "audit-service";
    public static final String AUDIT_WEB_CLIENT_BEAN = "auditWebClient";
    public static final String AUDIT_RESILIENT_CALLER_BEAN = "auditResilientCaller";
    public static final int MAX_CONCURRENT_CALLS = 2;
    public static final int BREAKER_WINDOW = 10;
    public static final int BREAKER_MIN_CALLS = 5;
    public static final float BREAKER_FAILURE_PERCENT = 50f;
    public static final Duration BREAKER_OPEN = Duration.ofSeconds(30);
}
