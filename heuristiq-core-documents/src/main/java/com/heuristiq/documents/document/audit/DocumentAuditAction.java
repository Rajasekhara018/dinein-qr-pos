package com.heuristiq.documents.document.audit;

public enum DocumentAuditAction {
    UPLOAD,
    /** An upload was refused; has no document id. */
    UPLOAD_REJECTED,
    /** The malware-scan outcome of an upload (verdict and provider in the message). */
    SCAN_RESULT,
    DOWNLOAD,
    /** A time-limited download link was issued. */
    DOWNLOAD_URL_ISSUED,
    DELETE,
    LEGAL_HOLD,
    /** A reviewer confirmed or corrected the last day a document is valid (D12). */
    VALID_UNTIL,
    /** Metadata of a soft-deleted document was purged by the retention job. */
    PURGE
}
