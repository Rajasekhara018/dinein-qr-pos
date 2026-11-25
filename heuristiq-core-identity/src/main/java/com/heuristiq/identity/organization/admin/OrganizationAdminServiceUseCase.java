package com.heuristiq.identity.organization.admin;

import com.heuristiq.identity.organization.Tenant;

/** Gives an organization its administrator: an account, the organization's admin role, and a membership. */
public interface OrganizationAdminServiceUseCase {

    record ProvisionInput(String tenantId, CreateOrganizationAdminRequest request) {}

    /**
     * Finds or creates the account, makes it an active member of the organization, and gives it the organization's
     * administrator role (created on first use, owned by the organization). Safe to repeat.
     */
    OrganizationAdminResult provision(ProvisionInput input);

    /**
     * Brings an organization's existing administrator role up to date: every privilege the role should have and access to
     * every application the organization has. Does nothing for an organization that never set an administrator up.
     *
     * @return true when the organization has an administrator role
     */
    boolean refreshRole(Tenant tenant);

    /** Gives an administrator role access to one application; safe to repeat. */
    void grantAccess(String roleCode, String applicationCode);
}
