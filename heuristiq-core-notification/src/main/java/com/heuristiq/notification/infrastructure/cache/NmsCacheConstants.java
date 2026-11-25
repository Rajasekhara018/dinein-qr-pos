package com.heuristiq.notification.infrastructure.cache;

/**
 * Fixed values of NMS's read caches: metric names and key shapes. The tunable values (TTL, size, switches) are not here;
 * they come from {@code nms.cache.*} ({@code NmsProperties.Cache}) and these constants only seed their defaults.
 */
public final class NmsCacheConstants {

    /** Metric name (the {@code cache} tag) of the organization-profile cache. */
    public static final String ORGANIZATION_PROFILE_CACHE = "nms.organization-profile";

    /** Metric name (the {@code cache} tag) of the resolved-template cache. */
    public static final String TEMPLATE_CACHE = "nms.template";

    /** Timer recording how long loading a missing cache entry takes (tagged with the cache name). */
    public static final String LOAD_TIMER_NAME = "cache.load.duration";

    /** Key part for a shared template (no organization). Tenant keys carry {@link #TENANT_KEY_PREFIX}, so the two can never collide. */
    public static final String SHARED_TENANT_KEY = "shared";

    /** Prefix of an organization's key part. */
    public static final String TENANT_KEY_PREFIX = "tenant:";

    public static final long DEFAULT_PROFILE_TTL_SECONDS = 300;
    /** A "profile not found" answer is kept briefly: a new organization should be picked up quickly. */
    public static final long DEFAULT_PROFILE_NOT_FOUND_TTL_SECONDS = 30;
    public static final long DEFAULT_PROFILE_MAX_SIZE = 500;

    public static final long DEFAULT_TEMPLATE_TTL_SECONDS = 60;
    /** A "no template" answer is kept very briefly so a newly created template is used almost at once. */
    public static final long DEFAULT_TEMPLATE_NOT_FOUND_TTL_SECONDS = 15;
    public static final long DEFAULT_TEMPLATE_MAX_SIZE = 1000;

    /** Upper bounds accepted for configuration. */
    public static final long MAX_TTL_SECONDS = 86_400;
    public static final long MAX_ENTRIES = 100_000;

    private NmsCacheConstants() {
    }
}
