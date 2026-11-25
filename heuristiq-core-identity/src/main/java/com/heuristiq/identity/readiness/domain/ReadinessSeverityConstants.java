package com.heuristiq.identity.readiness.domain;

/** Fixed values of the per-organization step severity (Required / Recommended) feature. */
public final class ReadinessSeverityConstants {

    /** Error codes returned in the failure envelope. */
    public static final String ERROR_SEVERITY_LOCKED = "READINESS_SEVERITY_LOCKED";
    public static final String ERROR_SEVERITY_INVALID = "READINESS_SEVERITY_INVALID";
    public static final String ERROR_STEP_NOT_FOUND = "READINESS_STEP_NOT_FOUND";

    /** Message keys Rider_Web translates. */
    public static final String KEY_SEVERITY_LOCKED = "readiness.severity.locked";
    public static final String KEY_SEVERITY_INVALID = "readiness.severity.invalid";
    public static final String KEY_STEP_NOT_FOUND = "readiness.severity.stepNotFound";

    /** English fallback texts of the keys above. */
    public static final String SEVERITY_LOCKED_MESSAGE = "This step is always required and cannot be changed: ";
    public static final String SEVERITY_INVALID_MESSAGE = "The severity must be BLOCK or WARN";
    public static final String STEP_NOT_FOUND_MESSAGE = "This application has no such readiness step: ";

    /** Domain audit remarks. */
    public static final String AUDIT_REMARK_CHANGED = "Readiness step severity changed";
    public static final String AUDIT_REMARK_RESET = "Readiness step severity reset to its default";
    public static final String AUDIT_REMARK_RESET_ALL = "All readiness step severities of the application reset to their defaults";

    private ReadinessSeverityConstants() {
    }
}
