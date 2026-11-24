package com.heuristiq.paymentservice.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;

/**
 * Which organization's records the current caller may reach. A record carries the organization it was created in
 * ({@code tenantId}); a caller with an organization sees that organization's records, a backend {@code SERVICE} caller
 * with none (and work with no caller at all, such as a scheduled job) is not limited, and a user whose session has no
 * organization sees only records that have none. Legacy unowned records require organization backfill;
 * an organization-scoped caller cannot read unowned financial data.
 */
public final class TenantAccess {

    private TenantAccess() {
    }

    /** What the caller can reach: trusted system work, exactly one organization, or legacy unowned records only. */
    public record Scope(boolean unrestricted, String tenantId) {

        public boolean hasTenant() {
            return StringUtils.hasText(tenantId);
        }

        /** True when a record of {@code resourceTenantId} is within this scope. */
        public boolean allows(String resourceTenantId) {
            return unrestricted || (hasTenant() ? tenantId.equals(resourceTenantId) : !StringUtils.hasText(resourceTenantId));
        }
    }

    public static Scope scope() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthPrincipal principal)) {
            return new Scope(true, null);
        }
        if (StringUtils.hasText(principal.tenantId())) {
            return new Scope(false, principal.tenantId().trim());
        }
        return new Scope(AuthPrincipal.SERVICE_ROLE.equals(principal.role()), null);
    }

    /** The organization to store on a new record: the caller's, or null when the caller has none. */
    public static String stamp() {
        Scope scope = scope();
        return scope.hasTenant() ? scope.tenantId() : null;
    }

    /** True when the current caller may reach a record of the given organization. */
    public static boolean canSee(String resourceTenantId) {
        return scope().allows(resourceTenantId);
    }

    /**
     * JPQL to append to a {@code WHERE} that limits the alias's rows to the caller's organization; empty when the caller is
     * not limited. The value, when there is one, is bound by the caller as {@code :tenantId}.
     */
    public static String jpqlClause(String alias) {
        Scope scope = scope();
        if (scope.unrestricted()) {
            return "";
        }
        return scope.hasTenant()
                ? " AND " + alias + ".tenantId = :tenantId"
                : " AND " + alias + ".tenantId IS NULL";
    }
}
