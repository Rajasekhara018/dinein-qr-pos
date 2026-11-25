package com.heuristiq.audit.adapter.in.web;

import java.util.List;

/**
 * Header names, response values and CSV columns of the web adapter.
 */
public final class WebConstants {

    private WebConstants() {
    }

    public static final String RETRY_AFTER_SECONDS = "60";
    public static final String RETRY_AFTER_HEADER = "Retry-After";
    public static final String CORRELATION_HEADER = "X-Correlation-ID";
    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String RATE_LIMITED_CODE = "RATE_LIMITED";
    public static final String AUTH_REQUIRED_MESSAGE = "Authentication required";

    /** Base path of the service. */
    public static final String API_BASE = "/api/v1/audit";
    public static final String INTERNAL_BASE = API_BASE + "/internal";
    public static final String EVENTS_BASE = API_BASE + "/events";
    /** Self-service activity of the signed-in user; deliberately outside the privilege catalog's /events paths. */
    public static final String MY_EVENTS_BASE = API_BASE + "/me/events";
    public static final int MY_EVENTS_MAX_PAGE_SIZE = 50;
    public static final String INTERNAL_EVENTS = "/events";
    public static final String SCREEN_VIEWS = "/screen-views";
    public static final String EXPORT = "/export";
    public static final String EXPORT_PAYLOADS = "/export/payloads";
    public static final String FACETS = "/facets";
    public static final String BY_CORRELATION = "/correlation/{correlationId}";
    public static final String PAYLOAD = "/{id}/payload";
    public static final String BY_ID = "/{id}";
    public static final String CSV_FILE_PREFIX = "audit-events-";
    public static final String CSV_PAYLOAD_FILE_PREFIX = "audit-events-payloads-";
    public static final String CSV_TYPE = "text";
    public static final String CSV_SUBTYPE = "csv";
    public static final String DEFAULT_PAGE_SIZE = "50";
    public static final String CSV_LINE_END = "\r\n";
    /** Start of the Content-Disposition value of a CSV download; the file name follows. */
    public static final String CSV_ATTACHMENT_PREFIX = "attachment; filename=\"";
    /** End of the Content-Disposition value of a CSV download (extension plus closing quote). */
    public static final String CSV_ATTACHMENT_SUFFIX = ".csv\"";
    /** Characters that make a spreadsheet treat a cell as a formula (CSV injection guard). */
    public static final String CSV_FORMULA_STARTS = "=+-@\t\r";
    /** Separator between the items of a list held in one CSV cell (roles, privilege ids). */
    public static final String CSV_LIST_SEPARATOR = "|";
    /** Separator between key=value pairs held in one CSV cell (details, headers). */
    public static final String CSV_PAIR_SEPARATOR = "; ";

    /** Column headings, in output order. */
    public static final List<String> CSV_HEADER = List.of("id", "eventId", "eventType", "occurredAt", "sourceService",
            "userId", "userType", "role", "roles", "privilegeIds", "feature", "module", "screenKey", "method", "path",
            "statusCode", "outcome", "durationMs", "correlationId", "requestId", "ipAddress", "userAgent",
            "appChannel", "tenantId", "details");

    /** Columns appended after everything else (after the payload columns on the payload export). */
    public static final List<String> CSV_TRAILING_HEADER = List.of("subjectType", "subjectId", "reasonCode");

    /** Payload columns appended by {@code CsvWriter.writeWithPayloads}. */
    public static final List<String> PAYLOAD_HEADER = List.of("requestHeaders", "responseHeaders", "requestBody",
            "responseBody", "requestBodyTruncated", "responseBodyTruncated", "requestBodySize", "responseBodySize",
            "bodyContentType");

}
