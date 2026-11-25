package com.heuristiq.identity.pos.adapter.in;

import com.heuristiq.identity.common.UamCommonConstants;
import com.heuristiq.identity.organization.OrgWideConstants;
import com.heuristiq.identity.organization.admin.OrganizationAdminConstants;
import com.heuristiq.identity.organization.repository.TenantRepository;
import com.heuristiq.identity.pos.application.PosCaller;
import com.heuristiq.identity.security.TenantContext;
import com.heuristiq.identity.users.repository.UamUserRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Builds the {@link PosCaller} of a request. Tenant-wide callers are SUPER_ADMIN, organization-wide callers (they work on the
 * organization named in the path), the session organization's own admin role ({@code <CODE>_ADMIN}) and
 * {@code POS_RESTAURANT_ADMIN}. Everyone else is placed in the organization of their session and scoped to their branches; the
 * service answers "not found" when the path names another organization.
 */
@Component
@ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "postgres")
class PosCallers {

    static final String RESTAURANT_ADMIN_ROLE = "POS_RESTAURANT_ADMIN";
    private static final String ROLE_PREFIX = "ROLE_";

    private final TenantRepository tenants;
    private final UamUserRepository users;

    PosCallers(TenantRepository tenants, UamUserRepository users) {
        this.users = users;
        this.tenants = tenants;
    }

    /** The caller of the current request, for the organization named in the path. */
    PosCaller current(String pathOrganizationId) {
        return current(SecurityContextHolder.getContext().getAuthentication(), pathOrganizationId);
    }

    PosCaller current(Authentication authentication, String pathOrganizationId) {
        String userId = authentication == null ? null : authentication.getName();
        if (hasAuthority(authentication, UamCommonConstants.SUPER_ADMIN_AUTHORITY)
                || hasAuthority(authentication, OrgWideConstants.AUTHORITY)) {
            return new PosCaller(userId, pathOrganizationId, true);
        }
        String sessionTenant = TenantContext.get();
        return new PosCaller(userId, sessionTenant, isAdminOf(authentication, sessionTenant));
    }

    private static boolean hasAuthority(Authentication authentication, String authority) {
        return authentication != null
                && authentication.getAuthorities().stream().anyMatch(a -> authority.equals(a.getAuthority()));
    }

    /** Roles are user-level, so POS_RESTAURANT_ADMIN counts only in the user's home tenant; unknown user or tenant fails closed. */
    private boolean isHomeTenant(Authentication authentication, String sessionTenant) {
        if (sessionTenant == null || authentication.getName() == null) {
            return false;
        }
        return users.findById(authentication.getName()).map(u -> sessionTenant.equals(u.getTenantId())).orElse(false);
    }

    private boolean isAdminOf(Authentication authentication, String sessionTenant) {
        if (authentication == null) {
            return false;
        }
        if (hasAuthority(authentication, ROLE_PREFIX + RESTAURANT_ADMIN_ROLE) && isHomeTenant(authentication, sessionTenant)) {
            return true;
        }
        if (sessionTenant == null) {
            return false;
        }
        String adminRole = tenants.findById(sessionTenant).map(t -> t.getCode()).filter(code -> !code.isBlank())
                .map(OrganizationAdminConstants::adminRoleCode).orElse(null);
        return adminRole != null && authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                .anyMatch(a -> (ROLE_PREFIX + adminRole).equals(a));
    }
}
