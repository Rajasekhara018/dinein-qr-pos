package com.heuristiq.identity.organization.legalprofile.adapter.in;

import com.heuristiq.identity.organization.OrgWideConstants;
import com.heuristiq.identity.organization.legalprofile.domain.LegalProfileException;
import com.heuristiq.identity.security.TenantContext;
import com.heuristiq.identity.security.TenantGuard;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Who may reach an organization's legal profile: an organization-wide caller (the token's {@code orgWide} claim, granted by
 * privilege) or a SUPER_ADMIN reaches every organization; any other signed-in person only the organization of their session
 * (the token's tenant). A signed-in person whose token names no organization reaches none. Refusals answer "not found", so
 * ids of other organizations are not confirmed.
 */
final class LegalProfileAccess {

    private LegalProfileAccess() {
    }

    static void assertCanReach(String organizationId) {
        if (TenantGuard.isSuperAdmin() || isOrgWide() || !TenantGuard.isSignedInUser()) {
            return;
        }
        String sessionOrganization = TenantContext.get();
        if (sessionOrganization == null || !sessionOrganization.equals(organizationId)) {
            throw LegalProfileException.organizationNotFound();
        }
    }

    private static boolean isOrgWide() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> OrgWideConstants.AUTHORITY.equals(authority.getAuthority()));
    }
}
