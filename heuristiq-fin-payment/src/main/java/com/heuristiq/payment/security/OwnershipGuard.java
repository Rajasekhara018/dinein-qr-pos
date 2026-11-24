package com.heuristiq.payment.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Extracts the authenticated principal from the security context and enforces
 * resource-ownership checks: a normal caller may only access their own data, while callers the gateway marked
 * as elevated (privilege PLATFORM_ELEVATED_ACCESS) and backend {@code SERVICE} callers are exempt.
 */
public final class OwnershipGuard {

    private OwnershipGuard() {}

    /**
     * The authenticated caller.
     *
     * @throws AccessDeniedException when nobody is authenticated
     */
    public static AuthPrincipal currentPrincipal() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthPrincipal principal)) {
            throw new AccessDeniedException("Not authenticated");
        }
        return principal;
    }

    /**
     * Requires the caller to be {@code resourceOwnerId}.
     *
     * @throws AccessDeniedException otherwise (HTTP 403)
     */
    public static void requireSelf(String resourceOwnerId) {
        if (!currentPrincipal().isSelf(resourceOwnerId)) {
            throw new AccessDeniedException("Access denied: you may only access your own data");
        }
    }

    /**
     * Requires the caller to be {@code resourceOwnerId} or a privileged (admin/service) caller.
     *
     * @throws AccessDeniedException otherwise (HTTP 403)
     */
    public static void requireSelfOrPrivileged(String resourceOwnerId) {
        AuthPrincipal principal = currentPrincipal();
        if (!principal.isPrivileged() && !principal.isSelf(resourceOwnerId)) {
            throw new AccessDeniedException("Access denied: you may only access your own data");
        }
    }

    /**
     * Requires an admin or backend service caller.
     *
     * @throws AccessDeniedException for every other caller (HTTP 403)
     */
    public static void requirePrivileged() {
        if (!currentPrincipal().isPrivileged()) {
            throw new AccessDeniedException("Access denied: administrator or service access required");
        }
    }

    /**
     * True when the caller is privileged or is one of the given parties (e.g. a payment's customer or
     * driver). Use it for reads where a foreign resource must look like it does not exist.
     */
    public static boolean isPartyOrPrivileged(String... partyIds) {
        AuthPrincipal principal = currentPrincipal();
        if (principal.isPrivileged()) {
            return true;
        }
        for (String id : partyIds) {
            if (principal.isSelf(id)) {
                return true;
            }
        }
        return false;
    }
}
