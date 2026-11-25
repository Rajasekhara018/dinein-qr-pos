package com.heuristiq.identity.organization.operatingmodel.adapter.out;

import com.heuristiq.identity.organization.operatingmodel.application.port.OrganizationDirectory;
import com.heuristiq.identity.organization.repository.TenantRepository;
import java.util.Optional;

/**
 * Organizations as kept by the tenant repository. Only the id and time zone are read, so the repository's per-instance cache
 * is harmless here; the schedule itself is in its own store.
 */
public class TenantOrganizationDirectory implements OrganizationDirectory {

    private final TenantRepository tenants;

    public TenantOrganizationDirectory(TenantRepository tenants) {
        this.tenants = tenants;
    }

    @Override
    public Optional<Organization> find(String organizationId) {
        return tenants.findById(organizationId).map(t -> new Organization(t.getId(), t.getTimeZone()));
    }
}
