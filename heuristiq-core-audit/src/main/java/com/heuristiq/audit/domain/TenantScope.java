package com.heuristiq.audit.domain;

/**
 * Which organizations' audit records a reader may reach. A record carries the organization it was created in
 * ({@code tenantId}); a record with none is a legacy record and is visible to everyone, so introducing organizations
 * never hides existing history.
 *
 * <ul>
 * <li>restricted with an organization: that organization's records plus records with none;</li>
 * <li>restricted without an organization (a session that has none): only records with none;</li>
 * <li>unrestricted: everything (an organization-wide caller who asked to look across organizations).</li>
 * </ul>
 *
 * @param unrestricted true when no organization filter applies
 * @param tenantId     the reader's organization, or null
 */
public record TenantScope(boolean unrestricted, String tenantId) {

    /** No organization filter. */
    public static final TenantScope ALL = new TenantScope(true, null);

    /**
     * Limits a reader to one organization (plus unowned records).
     *
     * @param tenantId the session's organization; blank means the session has none
     * @return the scope
     */
    public static TenantScope of(String tenantId) {
        return new TenantScope(false, tenantId == null || tenantId.isBlank() ? null : tenantId.trim());
    }

    /** @return true when the reader belongs to an organization */
    public boolean hasTenant() {
        return tenantId != null && !tenantId.isBlank();
    }

    /**
     * @param recordTenantId the organization of a record, or null
     * @return true when a record of that organization is within this scope
     */
    public boolean allows(String recordTenantId) {
        return unrestricted || recordTenantId == null || recordTenantId.isBlank()
                || (hasTenant() && tenantId.equals(recordTenantId));
    }
}
