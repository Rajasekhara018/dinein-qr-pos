package com.heuristiq.configclient;

import com.heuristiq.resilient.TenantIdPropagation;
import org.slf4j.MDC;

/**
 * Decides which organization a Config read is made for: the one the caller names, otherwise the organization of the
 * current request (the MDC key {@link TenantIdPropagation#MDC_KEY}, set by each service from the verified token),
 * otherwise none (shared values only).
 */
public final class CallerTenant {

    private CallerTenant() { }

    /**
     * @param explicit organization given by the caller; null or blank means "the current request's"
     * @return the trimmed organization id, or null when there is none
     */
    public static String resolve(String explicit) {
        if (explicit != null && !explicit.isBlank()) {
            return explicit.trim();
        }
        String fromMdc = MDC.get(TenantIdPropagation.MDC_KEY);
        return fromMdc == null || fromMdc.isBlank() ? null : fromMdc.trim();
    }
}
