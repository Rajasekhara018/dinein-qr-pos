package com.heuristiq.identity.pos.application;

/**
 * Who is calling. {@code tenantWide} is true for SUPER_ADMIN, organization-wide callers and holders of the tenant's admin
 * role; false means the caller is scoped to the branches they are assigned to.
 */
public record PosCaller(String userId, String tenantId, boolean tenantWide) {
}
