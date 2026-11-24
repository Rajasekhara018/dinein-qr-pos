package com.railcab.nms.domain.repository;

import java.util.ArrayList;
import java.util.List;

/**
 * Which organization's records a read may return. A record carries the organization it was created in
 * ({@code tenantId}); a scope limited to an organization sees that organization's records plus records
 * with no organization (stored before organizations existed), a scope with no organization sees only
 * those unowned records, and an unrestricted scope (backend callers, schedulers) sees everything.
 *
 * @param unrestricted true when nothing is filtered out
 * @param tenantId     the organization the caller belongs to; null or blank when it has none
 */
public record TenantScope(boolean unrestricted, String tenantId) {

    /** Nothing is filtered out. */
    public static final TenantScope UNRESTRICTED = new TenantScope(true, null);

    /** Limited to one organization plus unowned records; a blank id means unowned records only. */
    public static TenantScope limitedTo(String tenantId) {
        return new TenantScope(false, tenantId == null || tenantId.isBlank() ? null : tenantId.trim());
    }

    public boolean hasTenant() {
        return tenantId != null && !tenantId.isBlank();
    }

    /** True when a record of {@code resourceTenantId} is within this scope. */
    public boolean allows(String resourceTenantId) {
        return unrestricted
                || resourceTenantId == null || resourceTenantId.isBlank()
                || (hasTenant() && tenantId.equals(resourceTenantId));
    }

    /**
     * The values a stored {@code tenantId} may have inside this scope, with {@code null} standing for the unowned
     * records; meant for an {@code $in} style filter. Meaningless for an unrestricted scope.
     */
    public List<String> visibleTenantIds() {
        List<String> ids = new ArrayList<>(2);
        ids.add(null);
        if (hasTenant()) {
            ids.add(tenantId);
        }
        return ids;
    }

    /** The tenant id to bind to a query parameter: never null, so a database never sees an untyped null. */
    public String tenantIdOrEmpty() {
        return hasTenant() ? tenantId : "";
    }
}
