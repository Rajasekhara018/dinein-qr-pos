package com.heuristiq.uam.centralaudit;

import java.util.regex.Pattern;

/** Fixed values of the UAM to Rider_AuditService push. Per-environment values live in {@link CentralAuditProperties}. */
public final class CentralAuditConstants {

    private CentralAuditConstants() {
    }

    /** Name used for the circuit breaker, bulkhead and metrics of the audit downstream. */
    public static final String AUDIT_DOWNSTREAM = "audit-service";
    public static final String AUDIT_WEB_CLIENT_BEAN = "centralAuditWebClient";
    public static final String AUDIT_RESILIENT_CALLER_BEAN = "centralAuditResilientCaller";
    /** Ingest endpoint of the audit service (service-token protected, not routed by the gateway). */
    public static final String INGEST_PATH = "/api/v1/audit/internal/events";
    public static final String HEADER_SERVICE_TOKEN = "X-Rider-Service-Token";

    public static final String METRIC_INSERT_FAILED = "uam.audit.outbox.insert.failed";
    public static final String METRIC_SEND_FAILED = "uam.audit.outbox.send.failed";
    public static final String METRIC_DROPPED = "uam.audit.outbox.dropped";
    public static final String METRIC_OVERFLOW = "uam.audit.outbox.overflow";

    public static final String SOURCE_SERVICE = "rider-uam";
    public static final String EVENT_TYPE_DOMAIN = "DOMAIN";
    public static final String FEATURE = "uam";
    public static final String MODULE = "IDENTITY";
    public static final String OUTCOME_SUCCESS = "SUCCESS";
    public static final String OUTCOME_FAILURE = "FAILURE";

    public static final String DETAIL_ACTION = "action";
    public static final String DETAIL_STATUS = "status";
    public static final String DETAIL_USERNAME = "username";
    public static final String DETAIL_REMARKS = "remarks";
    public static final String DETAIL_DEVICE = "device";
    public static final String DETAIL_BEFORE_PREFIX = "before.";
    public static final String DETAIL_AFTER_PREFIX = "after.";

    /** The audit service allows 20 details per event and 500 characters per value. */
    public static final int MAX_DETAILS = 20;
    public static final int MAX_DETAIL_VALUE = 500;
    public static final int MAX_DETAIL_KEY = 64;
    public static final int MAX_SUBJECT_ID = 128;
    public static final int MAX_SUBJECT_TYPE = 40;
    public static final int MAX_REASON_CODE = 60;

    public static final Pattern UPPER_SNAKE = Pattern.compile("^[A-Z][A-Z0-9_]*$");
    public static final Pattern SUBJECT_ID = Pattern.compile("^[A-Za-z0-9_.:-]+$");
    public static final Pattern NON_ALNUM = Pattern.compile("[^A-Za-z0-9]+");
    public static final Pattern EDGE_UNDERSCORES = Pattern.compile("^_+|_+$");
    /** Redaction: mask text, keys whose values are credentials, and contact patterns (same rules as the admin audit publisher). */
    public static final String MASK = "***";
    public static final Pattern SECRET_KEY = Pattern.compile(
            "(?i).*(password|passwd|secret|token|authorization|cookie|api[-_]?key|otp|pin|cvv|card|signature).*");
    public static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    public static final Pattern PHONE = Pattern.compile("(?<!\\d)\\+?\\d[\\d\\s-]{8,13}\\d(?!\\d)");
    /** Column length of event_id and tenant_id in central_audit_outbox. */
    public static final int OUTBOX_ID_COLUMN_LENGTH = 64;

    public static final Pattern DETAIL_KEY_INVALID = Pattern.compile("[^A-Za-z0-9_.-]");
}
