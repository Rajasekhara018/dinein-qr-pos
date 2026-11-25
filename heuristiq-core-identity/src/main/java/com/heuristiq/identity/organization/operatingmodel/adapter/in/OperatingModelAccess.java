package com.heuristiq.identity.organization.operatingmodel.adapter.in;

import com.heuristiq.identity.organization.operatingmodel.application.OrganizationNotFoundException;
import com.heuristiq.identity.organization.operatingmodel.domain.OperatingModelConstants;
import com.heuristiq.identity.security.TenantContext;
import com.heuristiq.identity.security.TenantGuard;

/**
 * Who may reach an organization's operating model: a SUPER_ADMIN reaches every organization; any other signed-in person only
 * the organization of their session (the token's tenant). A signed-in person whose token names no organization reaches none.
 * Refusals answer "not found", so ids of other organizations are not confirmed.
 */
final class OperatingModelAccess {

    private OperatingModelAccess() {
    }

    static void assertCanReach(String organizationId) {
        if (TenantGuard.isSuperAdmin() || !TenantGuard.isSignedInUser()) {
            return;
        }
        String sessionOrganization = TenantContext.get();
        if (sessionOrganization == null || !sessionOrganization.equals(organizationId)) {
            throw new OrganizationNotFoundException(OperatingModelConstants.ERROR_ORGANIZATION_NOT_FOUND);
        }
    }
}
