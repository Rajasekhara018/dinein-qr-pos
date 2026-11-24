package com.heuristiq.uam.orgstructure.adapter.in;

import com.heuristiq.uam.common.UamCommonConstants;
import java.util.function.Supplier;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.heuristiq.uam.orgstructure.domain.OrgStructureException;
import com.heuristiq.uam.orgstructure.domain.ScopeMode;
import com.heuristiq.uam.security.TenantContext;
import com.heuristiq.uam.security.TenantGuard;

/**
 * Resolves who is calling, for which organization and with what reach. Identity always comes from the token; the
 * only request value honoured is {@value OrgStructureWebConstants#ORG_HEADER}, and only for an organization-wide caller (see
 * {@link com.heuristiq.uam.organization.OrgWideConstants}).
 *
 * <p>Only work with no signed-in person (internal calls) without a tenant is placed in the default organization; a
 * signed-in caller whose token names no organization is refused.
 */
public class CallerContext {

    private final Supplier<String> defaultTenantId;

    /** A fixed default organization id (null or blank: there is none). */
    public CallerContext(String defaultTenantId) {
        this(() -> defaultTenantId, true);
    }

    private CallerContext(Supplier<String> defaultTenantId, boolean unused) {
        this.defaultTenantId = defaultTenantId;
    }

    /** The default organization is looked up when needed (by its code), so its id may be generated rather than fixed. */
    public static CallerContext withDefault(Supplier<String> defaultTenantId) {
        return new CallerContext(defaultTenantId, true);
    }

    /**
     * The organization the call applies to: for an organization-wide caller the one named in {@value OrgStructureWebConstants#ORG_HEADER} if given,
     * otherwise the caller's own tenant, otherwise the default organization.
     */
    public String tenant() {
        if (isOrgWide()) {
            String requested = requestedOrg();
            if (requested != null) {
                return requested;
            }
        }
        String tenantId = TenantContext.get();
        if (tenantId != null && !tenantId.isBlank()) {
            return tenantId;
        }
        // A signed-in person is never placed in the default organization: their token must name one.
        String fallback = TenantGuard.isSignedInUser() ? null : defaultTenantId.get();
        if (fallback == null || fallback.isBlank()) {
            throw OrgStructureException.invalid("The caller is not associated with an organization");
        }
        return fallback;
    }

    /** The roles the caller holds, without the ROLE_ prefix. */
    static java.util.List<String> roles() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return java.util.List.of();
        }
        return auth.getAuthorities().stream().map(a -> a.getAuthority())
                .map(a -> a.startsWith("ROLE_") ? a.substring(5) : a).toList();
    }

    /** The signed-in user's id (the token subject), or null when nobody is signed in. */
    static String userId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? null : auth.getName();
    }

    static String actor(Authentication auth) {
        return auth == null ? null : auth.getName();
    }

    /** True for an organization-wide caller: the only kind whose {@value OrgStructureWebConstants#ORG_HEADER} is honoured. */
    static boolean isOrgWide() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(authority -> com.heuristiq.uam.organization.OrgWideConstants.AUTHORITY.equals(authority.getAuthority()));
    }

    /** SUPER_ADMIN sees the whole organization irrespective of position; everyone else is limited by position. */
    static boolean isSuperAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(authority -> UamCommonConstants.SUPER_ADMIN_AUTHORITY.equals(authority.getAuthority()));
    }

    /** The widest scope the caller is entitled to regardless of position (null when it depends on their position). */
    static ScopeMode positionIndependentScope() {
        return isSuperAdmin() ? ScopeMode.ORG_WIDE : null;
    }

    private static String requestedOrg() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servlet) {
            String value = servlet.getRequest().getHeader(OrgStructureWebConstants.ORG_HEADER);
            return value == null || value.isBlank() ? null : value.trim();
        }
        return null;
    }
}
