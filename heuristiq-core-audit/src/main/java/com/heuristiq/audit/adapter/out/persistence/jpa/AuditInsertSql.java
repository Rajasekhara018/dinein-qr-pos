package com.heuristiq.audit.adapter.out.persistence.jpa;

import java.util.List;

/**
 * SQL of the batched, duplicate-skipping inserts of {@link AuditEventBatchWriter}. Column lists follow the Flyway
 * schema (V1, V3, V4, V5); {@code id} of {@code audit_events} is left to its identity column.
 */
final class AuditInsertSql {

    /**
     * Rows per INSERT statement. One statement per chunk keeps the bind-parameter count (29 per event) well under
     * PostgreSQL's limit of 32767 even for the largest ingest batch (railcab.audit.ingest.max-batch-size, 500).
     */
    static final int ROWS_PER_STATEMENT = 500;

    static final String EVENTS_TABLE = "audit_events";
    static final String PAYLOADS_TABLE = "audit_event_payloads";

    /** Columns of {@code audit_events} written on insert, in bind order. */
    static final List<String> EVENT_COLUMNS = List.of("event_id", "event_type", "occurred_at", "received_at",
            "expire_at", "source_service", "user_id", "user_type", "role", "roles", "privilege_ids", "feature",
            "module", "screen_key", "method", "path", "status_code", "outcome", "duration_ms", "correlation_id",
            "request_id", "ip_address", "user_agent", "app_channel", "tenant_id", "subject_type", "subject_id",
            "reason_code", "details");

    /** Columns of {@code audit_event_payloads} written on insert, in bind order. */
    static final List<String> PAYLOAD_COLUMNS = List.of("event_id", "request_headers", "response_headers",
            "request_body", "response_body", "request_body_truncated", "response_body_truncated", "request_body_size",
            "response_body_size", "body_content_type", "expire_at");

    /** Columns holding JSON, bound as text and cast. */
    static final List<String> JSONB_COLUMNS = List.of("details", "request_headers", "response_headers");

    static final String PLACEHOLDER = "?";
    static final String JSONB_PLACEHOLDER = "CAST(? AS jsonb)";

    /** Skips a row whose event_id is already stored (unique index / primary key) instead of failing the statement. */
    static final String ON_CONFLICT_EVENT_ID = " ON CONFLICT (event_id) DO NOTHING";

    /** Returns the event_id of each row actually inserted (skipped duplicates return nothing). */
    static final String RETURNING_EVENT_ID = " RETURNING event_id";

    static final String EVENT_ID_COLUMN = "event_id";

    private AuditInsertSql() {
    }

    /**
     * @param table   the table
     * @param columns its columns, in bind order
     * @param rows    the number of rows in the statement
     * @return {@code INSERT INTO table (cols) VALUES (...), (...) ON CONFLICT (event_id) DO NOTHING}
     */
    static String insertSkippingDuplicates(String table, List<String> columns, int rows) {
        StringBuilder row = new StringBuilder("(");
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                row.append(',');
            }
            row.append(JSONB_COLUMNS.contains(columns.get(i)) ? JSONB_PLACEHOLDER : PLACEHOLDER);
        }
        row.append(')');
        StringBuilder sql = new StringBuilder("INSERT INTO ").append(table).append(" (")
                .append(String.join(",", columns)).append(") VALUES ");
        for (int r = 0; r < rows; r++) {
            if (r > 0) {
                sql.append(',');
            }
            sql.append(row);
        }
        return sql.append(ON_CONFLICT_EVENT_ID).toString();
    }
}
