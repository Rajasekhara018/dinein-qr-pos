package com.heuristiq.identity.pos.adapter.out;

/** Fixed values of the POS stores (SQL; migration V44). Every statement filters on {@code tenant_id}. */
final class PosAdapterConstants {

    static final String ERROR_STALE_VERSION = "pos.staleVersion";
    static final String FIELD_VERSION = "version";
    static final String ERROR_CODE_TAKEN = "pos.branch.codeTaken";
    static final String FIELD_CODE = "code";
    static final String LANGUAGE_SEPARATOR = ",";

    private static final String ENTITY_COLUMNS = "id, tenant_id, country, legal_name, currency, address_line1, address_line2, "
            + "address_city, address_region, address_postal_code, address_country, invoice_prefix, financial_year_start_month, "
            + "status, version, updated_at, updated_by";

    /** Parameters: tenant id, id. */
    static final String SELECT_ENTITY_SQL = "SELECT " + ENTITY_COLUMNS + " FROM pos_legal_entity WHERE tenant_id = ? AND id = ?";

    /** Parameter: tenant id. */
    static final String LIST_ENTITIES_SQL = "SELECT " + ENTITY_COLUMNS + " FROM pos_legal_entity WHERE tenant_id = ? ORDER BY legal_name, id";

    /** Parameters: tenant id, legal entity id. */
    static final String SELECT_TAX_IDS_SQL = "SELECT type, value FROM pos_legal_entity_tax_id WHERE tenant_id = ? AND legal_entity_id = ? ORDER BY type";

    static final String INSERT_ENTITY_SQL = "INSERT INTO pos_legal_entity (" + ENTITY_COLUMNS
            + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    /** Parameters: country, legal name, currency, address (6), invoice prefix, financial year month, status, version, updated at, updated by, id, tenant id, expected version. */
    static final String UPDATE_ENTITY_SQL = "UPDATE pos_legal_entity SET country = ?, legal_name = ?, currency = ?, address_line1 = ?, "
            + "address_line2 = ?, address_city = ?, address_region = ?, address_postal_code = ?, address_country = ?, invoice_prefix = ?, "
            + "financial_year_start_month = ?, status = ?, version = ?, updated_at = ?, updated_by = ? "
            + "WHERE id = ? AND tenant_id = ? AND version = ?";

    /** Parameters: tenant id, legal entity id. */
    static final String DELETE_TAX_IDS_SQL = "DELETE FROM pos_legal_entity_tax_id WHERE tenant_id = ? AND legal_entity_id = ?";

    /** Parameters: legal entity id, tenant id, type, value. */
    static final String INSERT_TAX_ID_SQL = "INSERT INTO pos_legal_entity_tax_id (legal_entity_id, tenant_id, type, value) VALUES (?, ?, ?, ?)";

    private static final String BRANCH_COLUMNS = "id, tenant_id, legal_entity_id, code, name, time_zone, locale, languages, phone, email, "
            + "address_line1, address_line2, address_city, address_region, address_postal_code, address_country, status, version, "
            + "updated_at, updated_by";

    /** Parameters: tenant id, id. */
    static final String SELECT_BRANCH_SQL = "SELECT " + BRANCH_COLUMNS + " FROM pos_branch WHERE tenant_id = ? AND id = ?";

    /** Parameter: tenant id. */
    static final String LIST_BRANCHES_SQL = "SELECT " + BRANCH_COLUMNS + " FROM pos_branch WHERE tenant_id = ? ORDER BY code, id";

    /** Parameters: tenant id, legal entity id. */
    static final String ANY_BRANCH_FOR_ENTITY_SQL = "SELECT EXISTS (SELECT 1 FROM pos_branch WHERE tenant_id = ? AND legal_entity_id = ?)";

    static final String INSERT_BRANCH_SQL = "INSERT INTO pos_branch (" + BRANCH_COLUMNS
            + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    /** Parameters: legal entity id, code, name, time zone, locale, languages, phone, email, address (6), status, version, updated at, updated by, id, tenant id, expected version. */
    static final String UPDATE_BRANCH_SQL = "UPDATE pos_branch SET legal_entity_id = ?, code = ?, name = ?, time_zone = ?, locale = ?, "
            + "languages = ?, phone = ?, email = ?, address_line1 = ?, address_line2 = ?, address_city = ?, address_region = ?, "
            + "address_postal_code = ?, address_country = ?, status = ?, version = ?, updated_at = ?, updated_by = ? "
            + "WHERE id = ? AND tenant_id = ? AND version = ?";

    /** Parameters: tenant id, branch id. */
    static final String SELECT_STAFF_SQL = "SELECT user_id FROM pos_branch_staff WHERE tenant_id = ? AND branch_id = ? ORDER BY user_id";

    /** Parameters: tenant id, branch id. */
    static final String DELETE_STAFF_SQL = "DELETE FROM pos_branch_staff WHERE tenant_id = ? AND branch_id = ?";

    /** Parameters: tenant id, branch id, user id, assigned by, assigned at. */
    static final String INSERT_STAFF_SQL = "INSERT INTO pos_branch_staff (tenant_id, branch_id, user_id, assigned_by, assigned_at) VALUES (?, ?, ?, ?, ?)";

    /** Parameters: tenant id, user id. */
    static final String BRANCH_IDS_OF_SQL = "SELECT branch_id FROM pos_branch_staff WHERE tenant_id = ? AND user_id = ? ORDER BY branch_id";

    private PosAdapterConstants() {
    }
}
