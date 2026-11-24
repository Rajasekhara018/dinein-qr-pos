package com.heuristiq.uam.orgstructure.adapter.out;

import org.springframework.stereotype.Component;

import com.heuristiq.uam.organization.DataScopeMode;
import com.heuristiq.uam.organization.OrganizationSettings;
import com.heuristiq.uam.organization.admin.OrganizationAdminConstants;
import com.heuristiq.uam.organization.repository.TenantRepository;
import com.heuristiq.uam.orgstructure.domain.DataScopeSource;

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
