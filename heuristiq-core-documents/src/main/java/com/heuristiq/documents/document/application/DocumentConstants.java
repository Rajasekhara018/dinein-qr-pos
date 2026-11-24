package com.heuristiq.documents.document.application;

import java.time.Duration;

/**
 * Fixed values of the document feature: lifecycle and scan status values, audit statuses, object-key rules, metric
 * names and the retention job's lock. Stable machine reason codes stay in
 * {@link com.heuristiq.documents.document.audit.DocumentReasonCodes}.
 */
public final class DocumentConstants {

    private DocumentConstants() {
    }

    // ---- document lifecycle status (stored on the metadata)
    public static final String STATUS_UPLOADED = "UPLOADED";
    public static final String STATUS_DELETED = "DELETED";

    // ---- scan status (stored on the metadata)
    public static final String SCAN_CLEAN = "CLEAN";
    public static final String SCAN_SKIPPED = "SKIPPED";
    public static final String SCAN_SKIPPED_PASSWORD_PROTECTED = "SKIPPED_PASSWORD_PROTECTED";
    /** Provider name recorded when no scan ran. */
    public static final String NO_PROVIDER = "none";

    // ---- scan verdict recorded on a refused upload's SCAN_RESULT audit event
    public static final String VERDICT_INFECTED = "INFECTED";
    public static final String VERDICT_NO_VERDICT = "NO_VERDICT";
    public static final String VERDICT_NOT_SCANNABLE = "NOT_SCANNABLE";
    public static final String VERDICT_BLOCKED = "BLOCKED";

    // ---- audit
    /** Status of an event whose operation worked. */
    public static final String STATUS_SUCCESS = "SUCCESS";
    /** Status of an upload that was refused. */
    public static final String STATUS_REJECTED = "REJECTED";
    /** Actor recorded for work no user triggered (the retention job). */
    public static final String SYSTEM_ACTOR = "SYSTEM";
    /** Longest message kept on a local audit event. */
    public static final int AUDIT_MAX_MESSAGE_LENGTH = 250;
    /** Longest category / document type copied from a refused upload onto an audit event. */
    public static final int AUDIT_MAX_FIELD_LENGTH = 128;

    // ---- storage object keys
    /** Path segment for a caller whose session has no organization. */
    public static final String NO_ORGANIZATION_SEGMENT = "no-organization";
    /** Path segment for an upload that names no document type. */
    public static final String GENERAL_TYPE_SEGMENT = "general";
    /** Longest accepted path segment; longer input is refused rather than truncated (a truncated tenant could collide). */
    public static final int MAX_SEGMENT_LENGTH = 128;
    /** Longest stored file name (after sanitising), keeping the whole key well under the 1024-byte object-key limit. */
    public static final int MAX_FILE_NAME_LENGTH = 120;
    /** Length of the random prefix put in front of a stored file name. */
    public static final int STORED_NAME_PREFIX_LENGTH = 8;

    // ---- metrics
    public static final String METRIC_UPLOADS = "railcab_dms_uploads_total";
    public static final String METRIC_DOWNLOADS = "railcab_dms_downloads_total";
    public static final String TAG_PROVIDER = "provider";
    public static final String TAG_OUTCOME = "outcome";
    public static final String TAG_REASON = "reason";
    public static final String OUTCOME_SUCCESS = "success";
    public static final String OUTCOME_FAILED = "failed";
    public static final String REASON_STORAGE_ERROR = "storage_error";

    // ---- retention job
    /** Distributed-lock key of the retention purge, so one replica purges at a time. */
    /** D12: refusals of the valid-until date. */
    public static final String VALID_UNTIL_IN_PAST = "The document has already expired: the valid-until date is before today";
    public static final String VALID_UNTIL_REQUIRED = "validUntil is required";
    public static final String VALID_UNTIL_OWN_DOCUMENT = "The owner of a document cannot confirm its valid-until date";
    public static final String RETENTION_LOCK_KEY = "dms-retention-purge";
    public static final Duration RETENTION_LOCK_LEASE = Duration.ofMinutes(5);

    // ---- retention policy mapping onto the common RUpload shape
    /** A retention ending within this many days maps to RETAIN_SHORT. */
    public static final long RETAIN_SHORT_MAX_DAYS = 90;
    /** A retention ending within this many days (and beyond the short one) maps to RETAIN_NORMAL. */
    public static final long RETAIN_NORMAL_MAX_DAYS = 365;
}
