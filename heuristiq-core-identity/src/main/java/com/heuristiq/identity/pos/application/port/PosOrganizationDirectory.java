package com.heuristiq.identity.pos.application.port;

/** Read access to the tenants and memberships that live outside the POS model. */
public interface PosOrganizationDirectory {

    boolean tenantExists(String tenantId);

    boolean isActiveMember(String tenantId, String userId);
}
