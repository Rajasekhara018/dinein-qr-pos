package com.heuristiq.configclient.city;

/** Fixed values of the city service-area lookup: wire path and fields, geometry constants and statuses. */
public final class CityConstants {

    /** Internal Config endpoint (relative to the Config API root) listing the organization's service areas. */
    public static final String SERVICE_AREAS_PATH = "/internal/cities/service-areas";

    /** Response envelope field holding the payload. */
    public static final String FIELD_RESPONSE_OBJECT = "responseObject";
    public static final String FIELD_CODE = "code";
    public static final String FIELD_NAME = "name";
    public static final String FIELD_TIER = "tier";
    public static final String FIELD_TIMEZONE = "timezone";
    public static final String FIELD_SERVICE_STATUS = "serviceStatus";
    public static final String FIELD_CENTER_LAT = "centerLat";
    public static final String FIELD_CENTER_LNG = "centerLng";
    public static final String FIELD_SERVICE_RADIUS_KM = "serviceRadiusKm";
    public static final String FIELD_BOUNDARY = "boundary";
    public static final String FIELD_TENANT_ID = "tenantId";
    public static final String FIELD_LOCATION_ID = "locationId";

    /** Service status of a city that takes bookings. */
    public static final String STATUS_LIVE = "LIVE";

    /** Mean earth radius used by the haversine formula, in km. */
    public static final double EARTH_RADIUS_KM = 6371.0088;
    /** Tolerance (degrees) within which a point counts as lying on a polygon edge (about 1 cm). */
    public static final double EDGE_EPSILON_DEG = 1e-7;
    /** Orientation (cross product, degrees squared) below which three points count as collinear. */
    public static final double ORIENTATION_EPSILON = 1e-12;
    /** Fewest vertices of a polygon. */
    public static final int MIN_POLYGON_POINTS = 3;

    public static final double MIN_LAT = -90.0;
    public static final double MAX_LAT = 90.0;
    public static final double MIN_LNG = -180.0;
    public static final double MAX_LNG = 180.0;

    /** Most organizations whose area list is kept in memory at once (least recently used is evicted). */
    public static final int MAX_CACHED_ORGANIZATIONS = 500;
    /** Cache key used for a caller without an organization (shared cities only). */
    public static final String NO_TENANT_KEY = "";

    private CityConstants() { }
}
