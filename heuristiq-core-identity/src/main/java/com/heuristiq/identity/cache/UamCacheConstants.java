package com.heuristiq.identity.cache;

import java.util.Set;

/** Fixed values of the UAM read caches: cache names, defaults, bounds and key sentinels. Per-environment values live in {@link UamCacheProperties}. */
public final class UamCacheConstants {

    /** Meter tag value ({@code cache=...}) of the per-caller privileges cache. */
    public static final String MY_PRIVILEGES = "uam.my-privileges";
    /** Meter tag value of the per-caller organizations cache. */
    public static final String MY_ORGANIZATIONS = "uam.my-organizations";
    /** Meter tag value of the organization (tenant) lookup cache. */
    public static final String TENANTS = "uam.tenants";
    /** Meter tag value of the active-application-codes-per-organization cache. */
    public static final String TENANT_APPLICATIONS = "uam.tenant-applications";

    /** Key part used when the caller has no organization; never null in a key. */
    public static final String NO_TENANT = "~none";
    /** Joins the session's role authorities in the scope part of a privileges key. */
    public static final String SCOPE_SEPARATOR = ",";
    /** Lookup kind: tenant by id. */
    public static final String LOOKUP_BY_ID = "id";
    /** Lookup kind: tenant by code (key value is lower-cased). */
    public static final String LOOKUP_BY_CODE = "code";

    public static final int DEFAULT_MY_PRIVILEGES_TTL_SECONDS = 30;
    public static final int DEFAULT_MY_ORGANIZATIONS_TTL_SECONDS = 30;
    public static final int DEFAULT_TENANTS_TTL_SECONDS = 120;
    public static final int DEFAULT_TENANT_APPLICATIONS_TTL_SECONDS = 120;
    public static final int DEFAULT_USER_KEYED_MAX_SIZE = 10_000;
    public static final int DEFAULT_TENANT_KEYED_MAX_SIZE = 2_000;

    /** Upper bound accepted for a TTL, so a typo cannot make stale permissions live for hours. */
    public static final int MAX_TTL_SECONDS = 600;
    /** Upper bound accepted for a cache size. */
    public static final int MAX_SIZE_LIMIT = 1_000_000;

    /** Repository methods that change stored data; a call to one evicts the dependent cache entries. */
    public static final Set<String> WRITE_METHODS = Set.of("save", "delete");

    private UamCacheConstants() {
    }
}
