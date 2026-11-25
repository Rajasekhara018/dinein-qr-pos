package com.heuristiq.identity.orgstructure.domain;

/** Where the organization-structure feature learns an organization's data-scope setting and who its administrators are. */
public interface DataScopeSource {

    /** True when the organization limits people to their own department and below. */
    boolean isDepartmentScoped(String tenantId);

    /** The code of the organization's administrator role, who always see the whole organization; null when unknown. */
    String adminRoleCode(String tenantId);
}
