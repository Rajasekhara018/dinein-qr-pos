package com.heuristiq.identity.organization.legalprofile.adapter.out;

import com.heuristiq.identity.organization.Tenant;
import com.heuristiq.identity.organization.legalprofile.application.port.LegalOrganizationDirectory;
import com.heuristiq.identity.organization.legalprofile.domain.LegalProfileException;
import com.heuristiq.identity.organization.repository.TenantRepository;
import java.util.Optional;

/**
 * Organizations as kept by the tenant repository; the legal name is the tenant's own {@code legalName} field. A change loads
 * the tenant afresh, sets only the legal name and saves it back, so every other field stays as stored.
 */
public class TenantLegalOrganizationDirectory implements LegalOrganizationDirectory {

    private final TenantRepository tenants;

    public TenantLegalOrganizationDirectory(TenantRepository tenants) {
        this.tenants = tenants;
    }

    @Override
    public Optional<Organization> find(String organizationId) {
        return tenants.findById(organizationId).map(t -> new Organization(t.getId(), t.getLegalName()));
    }

    @Override
    public void changeLegalName(String organizationId, String legalName) {
        Tenant tenant = tenants.findById(organizationId).orElseThrow(LegalProfileException::organizationNotFound);
        tenant.setLegalName(legalName);
        tenants.save(tenant);
    }
}
