package com.heuristiq.search.scope;

/**
 * Who is searching, as far as organization scoping cares.
 *
 * @param tenantId          the organization of the caller's session, or null
 * @param internal          true for a trusted internal service (no organization of its own: it is not filtered)
 * @param allOrganizations  true for an organization-wide caller who asked to look across organizations (not filtered)
 */
public record CallerScope(String tenantId, boolean internal, boolean allOrganizations) {

    public CallerScope(String tenantId, boolean internal) {
        this(tenantId, internal, false);
    }

    public boolean hasTenant() {
        return tenantId != null && !tenantId.isBlank();
    }

    /** True when no organization filter applies to this caller. */
    public boolean unrestricted() {
        return internal || allOrganizations;
    }
}
