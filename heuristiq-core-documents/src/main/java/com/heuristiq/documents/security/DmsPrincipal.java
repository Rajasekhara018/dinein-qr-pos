package com.railcab.dms.security;

import org.springframework.util.StringUtils;

/**
 * The caller as the gateway vouched for them.
 *
 * @param userId   the user id ({@code X-User-Id}); the internal service marker for a service-to-service call
 * @param role     the role ({@code X-User-Role})
 * @param tenantId the organization of the caller's session ({@code X-Tenant-Id}, set by the gateway from the verified
 *                 token), or null when there is none
 */
public record DmsPrincipal(String userId, String role, String tenantId) {

    public DmsPrincipal(String userId, String role) {
        this(userId, role, null);
    }

    public boolean hasIdentity() {
        return StringUtils.hasText(userId);
    }

    public boolean hasTenant() {
        return StringUtils.hasText(tenantId);
    }

    public boolean isInternalService() {
        return DmsSecurityConstants.INTERNAL_SERVICE_ROLE.equals(
                role == null ? null : role.replaceFirst(DmsSecurityConstants.ROLE_PREFIX_REGEX, ""));
    }
}
