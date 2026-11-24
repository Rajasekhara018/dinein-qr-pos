package com.heuristiq.uam.organization.legalprofile.adapter.out;

/** Fixed values of the legal-profile stores (SQL, collection and field names). */
final class LegalProfileAdapterConstants {

    /** Parameter: tenant id. */
    static final String SELECT_PROFILE_SQL = "SELECT entity_type, trading_name, registration_number, registration_country, "
            + "tax_registered, version, updated_by, updated_at FROM organization_legal_profile WHERE tenant_id = ?";

    /** Locks the organization's row for the rest of the transaction. Parameter: tenant id. */
    static final String LOCK_PROFILE_SQL = "SELECT version FROM organization_legal_profile WHERE tenant_id = ? FOR UPDATE";

    /** Serializes the first save of one organization (no row to lock yet). Parameter: tenant id. */
    static final String LOCK_FIRST_SAVE_SQL = "SELECT pg_advisory_xact_lock(hashtext('organization_legal_profile:' || ?))";

    /** Parameters: tenant id, entity type, trading name, registration number, registration country, tax registered, version, updated by, updated at. */
    static final String INSERT_PROFILE_SQL = "INSERT INTO organization_legal_profile (tenant_id, entity_type, trading_name, "
            + "registration_number, registration_country, tax_registered, version, updated_by, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

    /** Parameters: entity type, trading name, registration number, registration country, tax registered, new version, updated by, updated at, tenant id, expected version. */
    static final String UPDATE_PROFILE_SQL = "UPDATE organization_legal_profile SET entity_type = ?, trading_name = ?, "
            + "registration_number = ?, registration_country = ?, tax_registered = ?, version = ?, updated_by = ?, updated_at = ? "
            + "WHERE tenant_id = ? AND version = ?";

    /** Parameter: tenant id. */
    static final String SELECT_TAX_IDS_SQL = "SELECT tax_type, tax_value FROM organization_tax_id WHERE tenant_id = ?";
    static final String DELETE_TAX_IDS_SQL = "DELETE FROM organization_tax_id WHERE tenant_id = ?";
    /** Parameters: tenant id, tax type, tax value. */
    static final String INSERT_TAX_ID_SQL = "INSERT INTO organization_tax_id (tenant_id, tax_type, tax_value) VALUES (?, ?, ?)";

    /** Parameter: tenant id. */
    static final String SELECT_ADDRESSES_SQL = "SELECT kind, line1, line2, city, region, postal_code, country "
            + "FROM organization_address WHERE tenant_id = ?";
    static final String DELETE_ADDRESSES_SQL = "DELETE FROM organization_address WHERE tenant_id = ?";
    /** Parameters: tenant id, kind, line1, line2, city, region, postal code, country. */
    static final String INSERT_ADDRESS_SQL = "INSERT INTO organization_address "
            + "(tenant_id, kind, line1, line2, city, region, postal_code, country) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

    /** Mongo collection (same name as the Postgres table): one document per organization, id = tenant id. */
    static final String COLLECTION = "organization_legal_profile";
    static final String FIELD_ID = "_id";
    static final String FIELD_TENANT_ID = "tenantId";
    static final String FIELD_ENTITY_TYPE = "entityType";
    static final String FIELD_TRADING_NAME = "tradingName";
    static final String FIELD_REGISTRATION_NUMBER = "registrationNumber";
    static final String FIELD_REGISTRATION_COUNTRY = "registrationCountry";
    static final String FIELD_TAX_REGISTERED = "taxRegistered";
    static final String FIELD_TAX_IDS = "taxIds";
    static final String FIELD_ADDRESSES = "addresses";
    static final String FIELD_TYPE = "type";
    static final String FIELD_VALUE = "value";
    static final String FIELD_KIND = "kind";
    static final String FIELD_LINE1 = "line1";
    static final String FIELD_LINE2 = "line2";
    static final String FIELD_CITY = "city";
    static final String FIELD_REGION = "region";
    static final String FIELD_POSTAL_CODE = "postalCode";
    static final String FIELD_COUNTRY = "country";
    static final String FIELD_VERSION = "version";
    static final String FIELD_UPDATED_BY = "updatedBy";
    static final String FIELD_UPDATED_AT = "updatedAt";
    static final String INDEX_TAX_ID = "idx_legal_profile_tenant_tax_id";

    /** Version of a profile's first save. */
    static final long FIRST_VERSION = 1L;

    private LegalProfileAdapterConstants() {
    }
}
