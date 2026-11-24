package com.heuristiq.uam.cache;

import java.util.Locale;

/** Key types of the UAM caches. Keys always carry the organization so one organization's entry is never served to another. */
public final class UamCacheKeys {

    /**
     * A caller in a session organization; a missing organization becomes {@link UamCacheConstants#NO_TENANT}. {@code scope}
     * tells apart entries of one caller that differ by session (for the privileges, the roles their token carries); null
     * when the entry does not depend on the session.
     */
    public record TenantUserKey(String tenantId, String userId, String scope) {
        public static TenantUserKey of(String tenantId, String userId) {
            return of(tenantId, userId, null);
        }

        public static TenantUserKey of(String tenantId, String userId, String scope) {
            String tenant = tenantId == null || tenantId.isBlank() ? UamCacheConstants.NO_TENANT : tenantId;
            return new TenantUserKey(tenant, userId, scope);
        }
    }

    /** An organization looked up by id or by (lower-cased) code. */
    public record TenantLookupKey(String kind, String value) {
        public static TenantLookupKey byId(String id) {
            return new TenantLookupKey(UamCacheConstants.LOOKUP_BY_ID, id);
        }

        public static TenantLookupKey byCode(String code) {
            return new TenantLookupKey(UamCacheConstants.LOOKUP_BY_CODE, code.toLowerCase(Locale.ROOT));
        }
    }

    private UamCacheKeys() {
    }
}
