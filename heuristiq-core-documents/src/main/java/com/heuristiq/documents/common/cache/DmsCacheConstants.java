package com.heuristiq.documents.common.cache;

/** Fixed values of the DMS read caches: cache names (also the metric tag) and property defaults. */
public final class DmsCacheConstants {

    /** Cache of the effective document-type rule per (organization, type code). */
    public static final String DOCUMENT_TYPE_RULE_CACHE = "dms.document-type-rule";

    /** Timer meter for the time a cache spends loading from the database (tagged with the cache name). */
    public static final String LOAD_DURATION_METER = "cache.load.duration";
    /** Meter tag carrying the cache name (the tag Micrometer's own cache meters use). */
    public static final String CACHE_TAG = "cache";

    /** Property default (string form, for {@code @DefaultValue}): caching switched on. */
    public static final String DEFAULT_ENABLED = "true";
    /** Property default: seconds an entry lives; also the bound on how stale another instance can be. */
    public static final String DEFAULT_RULE_TTL_SECONDS = "60";
    /** Property default: maximum entries (organizations x document types in active use). */
    public static final String DEFAULT_RULE_MAX_SIZE = "5000";

    /** Upper bound accepted for a TTL (one day). */
    public static final long MAX_TTL_SECONDS = 86_400L;
    /** Upper bound accepted for a cache size. */
    public static final long MAX_SIZE = 1_000_000L;

    private DmsCacheConstants() {
    }
}
