package com.railcab.audit.application;

import java.util.List;

/**
 * The authenticated caller as read from the verified token.
 *
 * @param userId           the token subject
 * @param role             primary role without the ROLE_ prefix, or null
 * @param roles            every role without the ROLE_ prefix
 * @param tenantId         the organization of the session (the token's {@code tenantId} claim), or null
 * @param allOrganizations true only for an organization-wide caller who also asked to look across organizations
 *                         (see {@code CallerDetails}); such a caller is not limited to one organization
 */
public record CallerIdentity(String userId, String role, List<String> roles, String tenantId,
                             boolean allOrganizations) {

    /** A caller with no organization and no cross-organization view. */
    public CallerIdentity(String userId, String role, List<String> roles) {
        this(userId, role, roles, null, false);
    }
}
