package com.heuristiq.identity.orgstructure.adapter.out;

import org.springframework.stereotype.Component;

import com.heuristiq.identity.organization.DataScopeMode;
import com.heuristiq.identity.organization.OrganizationSettings;
import com.heuristiq.identity.organization.admin.OrganizationAdminConstants;
import com.heuristiq.identity.organization.repository.TenantRepository;
import com.heuristiq.identity.orgstructure.domain.DataScopeSource;

/** Reads the data-scope setting and the administrator role of an organization from its record. */
@Component
public class TenantDataScopeSource implements DataScopeSource {

    private final TenantRepository tenants;

    public TenantDataScopeSource(TenantRepository tenants) {
        this.tenants = tenants;
    }

    @Override
    public boolean isDepartmentScoped(String tenantId) {
        return tenants.findById(tenantId)
                .map(t -> OrganizationSettings.dataScopeOf(t.getSettings()) == DataScopeMode.DEPARTMENT)
                .orElse(false);
    }

    @Override
    public String adminRoleCode(String tenantId) {
        return tenants.findById(tenantId).map(t -> OrganizationAdminConstants.adminRoleCode(t.getCode())).orElse(null);
    }
}
