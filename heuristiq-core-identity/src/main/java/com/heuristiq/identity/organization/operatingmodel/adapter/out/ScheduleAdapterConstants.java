package com.heuristiq.uam.organization.operatingmodel.adapter.out;

/** Fixed values of the operating-model schedule stores (SQL, collection and field names). */
final class ScheduleAdapterConstants {

    /** Parameter: tenant id. */
    static final String SELECT_SQL = "SELECT effective_from, model FROM organization_operating_model_changes "
            + "WHERE tenant_id = ? ORDER BY effective_from";

    /** Serializes the changes of one organization for the rest of the transaction. Parameter: tenant id. */
    static final String LOCK_SQL = "SELECT pg_advisory_xact_lock(hashtext('organization_operating_model:' || ?))";

    /** Parameters: tenant id, effective from. */
    static final String DELETE_SQL = "DELETE FROM organization_operating_model_changes WHERE tenant_id = ? AND effective_from = ?";

    /** Parameters: tenant id, effective from, model, created by, created at. */
    static final String INSERT_SQL = "INSERT INTO organization_operating_model_changes "
            + "(tenant_id, effective_from, model, created_by, created_at) VALUES (?, ?, ?, ?, ?)";

    /** Mongo collection (same name as the Postgres table) and its fields. */
    static final String COLLECTION = "organization_operating_model_changes";
    static final String FIELD_ID = "_id";
    static final String FIELD_TENANT_ID = "tenantId";
    static final String FIELD_EFFECTIVE_FROM = "effectiveFrom";
    static final String FIELD_MODEL = "model";
    static final String FIELD_CREATED_BY = "createdBy";
    static final String FIELD_CREATED_AT = "createdAt";
    static final String INDEX_TENANT_DATE = "idx_operating_model_tenant_date";

    /** Separator of the document id {@code tenant|YYYY-MM-DD} (unique per organization and day). */
    static final String ID_SEPARATOR = "|";

    private ScheduleAdapterConstants() {
    }
}
