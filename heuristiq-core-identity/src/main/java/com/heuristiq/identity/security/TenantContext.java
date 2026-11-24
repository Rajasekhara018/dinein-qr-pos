package com.heuristiq.uam.security;

/**
 * Holds the tenant of the currently authenticated caller for the duration of a request.
 * Populated by {@link TenantContextFilter} and must be cleared at the end of every request
 * since servlet containers reuse threads across requests.
 */
public final class TenantContext {
    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(String tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static String get() {
        return CURRENT_TENANT.get();
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
