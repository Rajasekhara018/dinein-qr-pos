package com.heuristiq.uam.orgstructure.adapter.out;

import java.util.List;
import java.util.Set;

/**
 * Fixed values of the org structure outbound adapters: the Rider_DMS wire contract, the import CSV layout,
 * JDBC batch limits, the search index field and the shape of generated login passwords.
 */
public final class OrgStructureAdapterConstants {

    /** Bean name of the WebClient that talks to Rider_DMS. */
    public static final String DMS_WEB_CLIENT_BEAN = "orgDmsWebClient";

    /** Bean name of the resilient caller wrapping Rider_DMS calls. */
    public static final String DMS_RESILIENT_CALLER_BEAN = "dmsResilientCaller";

    /** Circuit-breaker / metrics name of the Rider_DMS dependency. */
    public static final String DMS_SERVICE_NAME = "dms-service";

    /** Header carrying the gateway forward key on calls to Rider_DMS. */
    public static final String GATEWAY_TOKEN_HEADER = "X-Gateway-Token";

    /** Rider_DMS documents endpoint. */
    static final String DOCUMENTS_PATH = "/api/v1/dms/documents";

    /** Rider_DMS document-metadata endpoint, relative to {@link #DOCUMENTS_PATH}. */
    static final String DOCUMENT_METADATA_PATH = "/metadata/{id}";

    /** Separator between the parts of a Rider_DMS storage object key. */
    static final String OBJECT_KEY_SEPARATOR = "/";

    /**
     * One part of a Rider_DMS storage object key. DMS only ever writes letters, digits, dot, underscore and hyphen into a key
     * ({@code DocumentObjectKeys.safePathPart}); anything else, or {@code ..}, is refused before it can reach a request path.
     */
    static final java.util.regex.Pattern OBJECT_KEY_PART = java.util.regex.Pattern.compile("^(?!\\.+$)[A-Za-z0-9._-]{1,200}$");

    /** Header naming the role the call is made as. */
    static final String ROLE_HEADER = "X-User-Role";

    /** Header naming the user the call is made for. */
    static final String USER_HEADER = "X-User-Id";

    /** The privileged service identity Rider_DMS lets read a document filed under another user. */
    static final String INTERNAL_ROLE = "INTERNAL_SERVICE";

    /** Rider_DMS scan statuses that allow the file to be read. */
    static final Set<String> READABLE_SCAN_STATUSES = Set.of("CLEAN", "SKIPPED");

    /** Content types accepted as a CSV upload. */
    static final Set<String> CSV_CONTENT_TYPES = Set.of("text/csv", "text/plain", "application/csv", "application/vnd.ms-excel");

    /** File extension accepted as a CSV upload. */
    static final String CSV_EXTENSION = ".csv";

    /** Import CSV column names (lower-case header cells). */
    static final String COL_EMPLOYEE_CODE = "employee_code";
    static final String COL_FULL_NAME = "full_name";
    static final String COL_EMAIL = "email";
    static final String COL_PHONE = "phone";
    static final String COL_DEPARTMENT_CODE = "department_code";
    static final String COL_POSITION_NAME = "position_name";
    static final String COL_MANAGER_CODE = "manager_employee_code";
    static final String COL_JOINED_ON = "joined_on";
    static final String COL_IS_HEAD = "is_head";

    /** Columns an import CSV must have. */
    static final List<String> REQUIRED_COLUMNS =
            List.of(COL_EMPLOYEE_CODE, COL_FULL_NAME, COL_DEPARTMENT_CODE, COL_POSITION_NAME);

    /** Department import CSV column names (lower-case header cells). */
    static final String COL_DEPT_CODE = "code";
    static final String COL_DEPT_NAME = "name";
    static final String COL_DEPT_PARENT_CODE = "parent_code";
    static final String COL_DEPT_UNIT_TYPE = "unit_type";
    static final String COL_DEPT_LADDER = "ladder";

    /** Columns a department import CSV must have ({@code parent_code} and {@code unit_type} are optional). */
    static final List<String> DEPARTMENT_REQUIRED_COLUMNS = List.of(COL_DEPT_CODE, COL_DEPT_NAME);

    // ladders and positions import file columns
    static final String COL_LADDER_NAME = "ladder";
    static final String COL_LADDER_POSITION = "position";
    static final String COL_LADDER_RANK = "rank";
    static final String COL_LADDER_MANAGERIAL = "managerial";
    // roles import file columns
    static final String COL_RULE_DEPARTMENT = "department_code";
    static final String COL_RULE_LADDER = "ladder";
    static final String COL_RULE_POSITION = "position_name";
    static final String COL_RULE_ROLE = "role_code";
    static final List<String> ROLE_RULE_REQUIRED_COLUMNS = List.of(COL_RULE_ROLE);

    static final List<String> LADDER_REQUIRED_COLUMNS = List.of(COL_LADDER_NAME, COL_LADDER_POSITION, COL_LADDER_RANK);

    /** Separator between a row's error codes in the stored errors column. */
    static final String IMPORT_ERROR_SEPARATOR = "|";

    /** Rows inserted per JDBC batch. */
    static final int IMPORT_INSERT_CHUNK = 1000;

    /** Width of the stored errors column; longer text is cut. */
    static final int IMPORT_ERRORS_COLUMN_LIMIT = 2000;

    /** Search index field holding a user's department codes. */
    public static final String DEPARTMENT_FIELD = "text10";

    /** Symbols used in generated login passwords (never sent by message, so the wider set is fine). */
    static final String LOGIN_PASSWORD_SYMBOLS = "!@#$%^&*-_=+";

    /** Length of a generated login password. */
    static final int LOGIN_PASSWORD_LENGTH = 28;

    private OrgStructureAdapterConstants() {
    }
}
