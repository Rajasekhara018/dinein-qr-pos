package com.railcab.audit.domain;

/** Result of the audited action. {@code DENIED} means the caller was refused (HTTP 401/403 or a privilege miss). */
public enum AuditOutcome {
    SUCCESS, FAILURE, DENIED;

    /** Derives the outcome from an HTTP status: 2xx/3xx succeed, 401/403 are denials, anything else failed. */
    public static AuditOutcome fromStatus(int status) {
        if (status == 401 || status == 403) {
            return DENIED;
        }
        return status >= 100 && status < 400 ? SUCCESS : FAILURE;
    }
}
