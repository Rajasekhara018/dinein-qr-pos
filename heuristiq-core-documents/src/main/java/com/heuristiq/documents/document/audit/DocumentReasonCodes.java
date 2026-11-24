package com.heuristiq.documents.document.audit;

import java.util.Set;

/**
 * Stable machine codes recorded on audit events ({@code reasonCode}). Operators and the troubleshooting screen filter on
 * them, so a released code is never renamed or reused.
 */
public final class DocumentReasonCodes {

    private DocumentReasonCodes() {
    }

    // ---- upload rejected: validation
    public static final String FILE_REQUIRED = "FILE_REQUIRED";
    public static final String FILE_TOO_LARGE = "FILE_TOO_LARGE";
    public static final String CONTENT_TYPE_NOT_ALLOWED = "CONTENT_TYPE_NOT_ALLOWED";
    public static final String EXTENSION_NOT_ALLOWED = "EXTENSION_NOT_ALLOWED";
    public static final String CHECKSUM_MISMATCH = "CHECKSUM_MISMATCH";
    /** The named document type is unknown, inactive, or belongs to another category. */
    public static final String DOCUMENT_TYPE_INVALID = "DOCUMENT_TYPE_INVALID";
    public static final String DOCUMENT_TYPES_UNAVAILABLE = "DOCUMENT_TYPES_UNAVAILABLE";
    public static final String PATH_INVALID = "PATH_INVALID";
    public static final String VALIDATION_FAILED = "VALIDATION_FAILED";

    // ---- upload rejected: scan
    public static final String BLOCKED_EXTENSION = "BLOCKED_EXTENSION";
    public static final String BLOCKED_CONTENT_TYPE = "BLOCKED_CONTENT_TYPE";
    public static final String DANGEROUS_CONTENT = "DANGEROUS_CONTENT";
    public static final String MALWARE_DETECTED = "MALWARE_DETECTED";
    /** A required scan could not run (disabled, provider down or failing). */
    public static final String SCAN_UNAVAILABLE = "SCAN_UNAVAILABLE";
    /** The provider answered without a real verdict (timeout, fail-open). */
    public static final String SCAN_INCONCLUSIVE = "SCAN_INCONCLUSIVE";
    public static final String PASSWORD_PROTECTED_NOT_SCANNABLE = "PASSWORD_PROTECTED_NOT_SCANNABLE";

    // ---- upload rejected: access and storage
    public static final String ACCESS_DENIED = "ACCESS_DENIED";
    public static final String NO_ORGANIZATION = "NO_ORGANIZATION";
    public static final String STORAGE_FAILED = "STORAGE_FAILED";
    public static final String UPLOAD_FAILED = "UPLOAD_FAILED";

    // ---- scan results that did not reject
    public static final String SCAN_CLEAN = "SCAN_CLEAN";
    public static final String SCAN_SKIPPED_DISABLED = "SCAN_SKIPPED_DISABLED";
    public static final String SCAN_SKIPPED_PASSWORD_PROTECTED = "SCAN_SKIPPED_PASSWORD_PROTECTED";

    // ---- purge
    public static final String PURGED_AFTER_RETENTION = "PURGED_AFTER_RETENTION";

    /** Rejection reasons that come from the malware scan step, which also get a SCAN_RESULT event. */
    public static final Set<String> SCAN_REJECTIONS = Set.of(BLOCKED_EXTENSION, BLOCKED_CONTENT_TYPE, DANGEROUS_CONTENT,
            MALWARE_DETECTED, SCAN_UNAVAILABLE, SCAN_INCONCLUSIVE, PASSWORD_PROTECTED_NOT_SCANNABLE);
}
