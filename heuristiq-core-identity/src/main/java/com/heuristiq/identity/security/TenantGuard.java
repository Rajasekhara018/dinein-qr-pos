package com.heuristiq.uam.security;

import com.heuristiq.uam.common.UamCommonConstants;
import com.heuristiq.uam.common.UamException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Enforces that a record fetched by ID belongs to the caller's own tenant, preventing one
 * tenant's admin from reading/modifying another tenant's data by guessing/reusing an ID.
 * Records with a null tenantId (shared/system-level, e.g. systemGroup/systemRole) and callers
 * with SUPER_ADMIN authority are exempt by design.
 */
public final class TenantGuard {

    private TenantGuard() {
    }

    public static void assertAccessible(String entityTenantId) {
        if (entityTenantId == null || isSuperAdmin()) {
            return;
        }
        String callerTenantId = TenantContext.get();
        if (callerTenantId == null || callerTenantId.equals(entityTenantId)) {
            return;
        }
        throw new UamException("Resource not found");
    }

    /** True when the caller is a platform administrator, who is exempt from organization scoping. */
    public static boolean isSuperAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> UamCommonConstants.SUPER_ADMIN_AUTHORITY.equals(authority.getAuthority()));
    }

    /** True for a signed-in person (token user): not anonymous, not an internal service call and not system work with no session. */
    public static boolean isSignedInUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof org.springframework.security.authentication.AnonymousAuthenticationToken)
                && authentication.getAuthorities().stream().noneMatch(a -> SecurityConstants.SERVICE_AUTHORITY.equals(a.getAuthority()));
    }

    /** True when someone is signed in and is not a platform administrator (system work with no session is neither). */
    public static boolean isSignedInNonSuperAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated() && !isSuperAdmin();
    }
}
