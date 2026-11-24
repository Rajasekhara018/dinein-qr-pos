package com.heuristiq.configclient.vehicle;

/** Fixed values of the vehicle-type catalogue lookup: wire path and fields, and the cache bound. */
public final class VehicleTypeConstants {

    /** Internal Config endpoint (relative to the Config API root) listing the organization's vehicle types. */
    public static final String VEHICLE_TYPES_PATH = "/internal/vehicle-types";

    /** Response envelope field holding the payload. */
    public static final String FIELD_RESPONSE_OBJECT = "responseObject";
    /** Field of a vehicle-type entry holding its code. */
    public static final String FIELD_CODE = "code";
    /** Field of a vehicle-type entry holding how many passengers it seats. */
    public static final String FIELD_PASSENGER_CAPACITY = "passengerCapacity";
    /** Fewest passengers a usable capacity can be; anything below is treated as not known. */
    public static final int MIN_CAPACITY = 1;

    /** Most organizations whose catalogue is kept in memory at once (least recently used is evicted). */
    public static final int MAX_CACHED_ORGANIZATIONS = 500;
    /** Cache key used for a caller without an organization (shared types only). */
    public static final String NO_TENANT_KEY = "";

    private VehicleTypeConstants() { }
}
