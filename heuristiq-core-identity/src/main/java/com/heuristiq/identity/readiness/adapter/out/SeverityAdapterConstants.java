package com.heuristiq.uam.readiness.adapter.out;

/** Fixed values of the severity override adapters (SQL, collection and field names, audit wording). */
final class SeverityAdapterConstants {

    /** Parameters: tenant id, application code. */
    static final String SELECT_ONE_SQL = "SELECT step_id, severity FROM readiness_severity_overrides "
            + "WHERE tenant_id = ? AND application_code = ? ORDER BY step_id";

    /** Parameter: tenant id. */
    static final String SELECT_ALL_SQL = "SELECT application_code, step_id, severity FROM readiness_severity_overrides "
            + "WHERE tenant_id = ? ORDER BY application_code, step_id";

    /** Parameters: tenant id, application code, step id. */
    static final String DELETE_SQL = "DELETE FROM readiness_severity_overrides "
            + "WHERE tenant_id = ? AND application_code = ? AND step_id = ?";

    /** Parameters: tenant id, application code, step id, severity, updated by, updated at. */
    static final String UPSERT_SQL = "INSERT INTO readiness_severity_overrides "
            + "(tenant_id, application_code, step_id, severity, updated_by, updated_at) VALUES (?, ?, ?, ?, ?, ?) "
            + "ON CONFLICT (tenant_id, application_code, step_id) DO UPDATE SET severity = EXCLUDED.severity, "
            + "updated_by = EXCLUDED.updated_by, updated_at = EXCLUDED.updated_at";

    /** Mongo collection (same name as the Postgres table) and its fields. */
    static final String COLLECTION = "readiness_severity_overrides";
    static final String FIELD_TENANT_ID = "tenantId";
    static final String FIELD_APPLICATION_CODE = "applicationCode";
    static final String FIELD_STEP_ID = "stepId";
    static final String FIELD_SEVERITY = "severity";
    static final String FIELD_UPDATED_BY = "updatedBy";
    static final String FIELD_UPDATED_AT = "updatedAt";
    static final String INDEX_TENANT_APPLICATION = "idx_readiness_severity_tenant_application";

    /** Separator of the document id {@code tenant|application|step} (unique per row). */
    static final String ID_SEPARATOR = "|";

    private SeverityAdapterConstants() {
    }
}
