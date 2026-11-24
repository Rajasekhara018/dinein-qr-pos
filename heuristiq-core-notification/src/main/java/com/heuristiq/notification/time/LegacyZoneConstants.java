package com.railcab.nms.time;

/**
 * Fixed names and defaults for the legacy time zone setting. The zone itself is configuration
 * ({@link LegacyZoneProperties}); only its name, default and the metric id are constants.
 */
public final class LegacyZoneConstants {

    /** Property prefix; the full key is {@code railcab.time.legacy-zone} (env {@code RAILCAB_TIME_LEGACY_ZONE}). */
    public static final String PROPERTY_PREFIX = "railcab.time";

    /** Property name below {@link #PROPERTY_PREFIX}. */
    public static final String PROPERTY_NAME = "legacy-zone";

    /** Full property key, used in log messages. */
    public static final String PROPERTY_KEY = PROPERTY_PREFIX + "." + PROPERTY_NAME;

    /**
     * Zone this service's zone-less timestamps were written in. It matches the zone of the service's image
     * (no zone pinned = UTC; Asia/Kolkata for images that pin IST). Changing it changes nothing until later stages use it.
     */
    public static final String DEFAULT_ZONE_ID = "Asia/Kolkata";

    /** Gauge: 1 when the JVM default zone offset differs from the legacy zone offset, else 0. */
    public static final String MISMATCH_METRIC = "railcab.time.legacy_zone.mismatch";

    private LegacyZoneConstants() {
    }
}
