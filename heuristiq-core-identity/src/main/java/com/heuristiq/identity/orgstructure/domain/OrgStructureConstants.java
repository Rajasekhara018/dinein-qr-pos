package com.heuristiq.uam.orgstructure.domain;

/** Fixed values of the organization-structure feature (statuses, line types, paging limits). */
public final class OrgStructureConstants {
    /** Deepest reporting chain read when listing everyone below a manager (guards against a runaway tree). */
    public static final int MAX_TEAM_DEPTH = 12;
    public static final String UNIT_ACTIVE = "ACTIVE";
    public static final String UNIT_ARCHIVED = "ARCHIVED";
    public static final String EMPLOYEE_ACTIVE = "ACTIVE";
    public static final String LINE_PRIMARY = "PRIMARY";

    public static final String DRAFT_OPEN = "OPEN";
    public static final String DRAFT_PUBLISHED = "PUBLISHED";
    public static final String DRAFT_DISCARDED = "DISCARDED";
    /** A published draft that was undone, restoring the structure to what it was before. */
    public static final String DRAFT_ROLLED_BACK = "ROLLED_BACK";

    public static final String IMPORT_VALIDATED = "VALIDATED";
    public static final String IMPORT_APPLYING = "APPLYING";
    public static final String IMPORT_APPLIED = "APPLIED";
    public static final String IMPORT_FAILED = "FAILED";
    public static final String IMPORT_CANCELLED = "CANCELLED";

    public static final String IMPORT_ROW_VALID = "VALID";
    public static final String IMPORT_ROW_INVALID = "INVALID";
    /** Employee, department and position are applied; the reporting line is still to do. */
    public static final String IMPORT_ROW_PLACED = "PLACED";
    public static final String IMPORT_ROW_APPLIED = "APPLIED";

    /** Upper bound for any keyset-paginated list so one request can never pull a whole 100k-person org. */
    public static final int MAX_PAGE_SIZE = 200;
    public static final int DEFAULT_PAGE_SIZE = 50;

    /**
     * Most logins one request creates. Each login hashes a password (twice), which is slow by design, so a bigger batch outlives
     * the gateway/proxy timeout: the logins are made but the caller never sees the answer. The caller pages through with the cursor.
     */
    public static final int MAX_LOGIN_BATCH = 50;

    /** Longest employee/department code accepted in an import row. */
    public static final int IMPORT_MAX_CODE_LENGTH = 100;

    /** Longest full name accepted in an import row. */
    public static final int IMPORT_MAX_NAME_LENGTH = 200;

    /** Separator between a login issue code and its detail (e.g. {@code UNKNOWN_ROLE:CODE}). */
    public static final String LOGIN_ISSUE_DETAIL_SEPARATOR = ":";

    private OrgStructureConstants() {
    }
}
