package com.heuristiq.identity.pos.adapter.out;

import com.heuristiq.identity.organization.membership.TenantMembershipStatus;
import com.heuristiq.identity.organization.membership.repository.TenantMembershipRepository;
import com.heuristiq.identity.organization.repository.TenantRepository;
import com.heuristiq.identity.pos.application.port.PosOrganizationDirectory;

/** Tenants and memberships as kept by UAM; a member is active when the membership status is ACTIVE. */
public class TenantPosOrganizationDirectory implements PosOrganizationDirectory {

    private final TenantRepository tenants;
    private final TenantMembershipRepository memberships;

    public TenantPosOrganizationDirectory(TenantRepository tenants, TenantMembershipRepository memberships) {
        this.tenants = tenants;
        this.memberships = memberships;
    }

    @Override
    public boolean tenantExists(String tenantId) {
        return tenants.findById(tenantId).isPresent();
    }

    @Override
    public boolean isActiveMember(String tenantId, String userId) {
        return memberships.findByTenantIdAndUserId(tenantId, userId)
                .map(m -> m.getStatus() == TenantMembershipStatus.ACTIVE).orElse(false);
    }
}
