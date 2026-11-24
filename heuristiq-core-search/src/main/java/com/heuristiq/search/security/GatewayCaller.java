package com.heuristiq.search.security;

/**
 * What the gateway vouches for about the caller beyond the user id and role, carried as the details of the
 * authentication.
 *
 * @param tenantId          the organization of the caller's session ({@code X-Tenant-Id}, set by the gateway from the
 *                          verified token), or null when the token carries none
 * @param allOrganizations  true when the caller is organization-wide ({@code X-Org-Wide}, set by the gateway from the
 *                          verified token) AND asked to look across organizations for this request
 *                          ({@code X-View-All-Organizations}); asking alone changes nothing
 */
public record GatewayCaller(String tenantId, boolean allOrganizations) {

    public GatewayCaller(String tenantId) {
        this(tenantId, false);
    }
}
