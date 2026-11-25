package com.heuristiq.notification.application.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;

import com.heuristiq.notification.domain.repository.TenantScope;

/**
 * Which organization's records the current caller may reach, and which organization a new record is stamped with.
 *
 * <ul>
 *   <li>A caller with an organization is limited to it plus records stored before organizations existed.</li>
 *   <li>A backend (internal) caller with no organization, and work with no caller at all (schedulers, vendor
 *       callbacks, Kafka consumers), is not limited.</li>
 *   <li>A user whose session has no organization sees only records that have none.</li>
 * </ul>
 *
 * The organization always comes from the verified token (or, for an internal caller, from a header honoured only
 * after the internal key or service token was verified), never from a request body.
 */
public final class TenantAccess {

    private TenantAccess() {
    }

    /** What the current caller is limited to. */
    public static TenantScope scope() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return TenantScope.UNRESTRICTED;
        }
        if (!(auth.getPrincipal() instanceof NmsPrincipal principal)) {
            return TenantScope.limitedTo(null);
        }
        return scopeOf(principal);
    }

    /** The scope of a known caller. */
    public static TenantScope scopeOf(NmsPrincipal principal) {
        if (StringUtils.hasText(principal.tenantId())) {
            return TenantScope.limitedTo(principal.tenantId());
        }
        return principal.internalService() ? TenantScope.UNRESTRICTED : TenantScope.limitedTo(null);
    }

    /** The organization to store on a new record: the caller's, or null when the caller has none. */
    public static String stamp() {
        TenantScope scope = scope();
        return scope.hasTenant() ? scope.tenantId() : null;
    }

    /**
     * The organization for work that may name one itself (a notification request): a caller with an organization
     * always gets its own; an unrestricted (internal or caller-less) one may supply {@code requested}; a user
     * without an organization stores none.
     */
    public static String stampOrRequested(String requested) {
        TenantScope scope = scope();
        if (scope.hasTenant()) {
            return scope.tenantId();
        }
        if (scope.unrestricted() && StringUtils.hasText(requested)) {
            return requested.trim();
        }
        return null;
    }

    /** True when the current caller may reach a record of the given organization. */
    public static boolean canSee(String resourceTenantId) {
        return scope().allows(resourceTenantId);
    }
}
