package com.heuristiq.configclient.city;

/**
 * Result of {@link CityLocator#locate}: which city a point is in.
 *
 * <p>{@link Status#UNKNOWN} means Config was unreachable and nothing was cached; callers must not refuse a booking
 * on UNKNOWN (treat it like "no city": global values, no serviceability check).
 *
 * @param status FOUND, OUTSIDE or UNKNOWN
 * @param area   the city found; null unless {@code status} is FOUND
 */
public record CityLookup(Status status, ServiceArea area) {

    /** Outcome of a lookup. */
    public enum Status {
        /** The point is inside a city's service area. */
        FOUND,
        /** The area list is known and the point is in none of them. */
        OUTSIDE,
        /** Config could not be reached and no list is cached. */
        UNKNOWN
    }

    public CityLookup {
        if (status == null) {
            throw new IllegalArgumentException("status is required");
        }
        if ((status == Status.FOUND) != (area != null)) {
            throw new IllegalArgumentException("area is required for FOUND and must be null otherwise");
        }
    }

    /** The point is in {@code area}. */
    public static CityLookup found(ServiceArea area) {
        return new CityLookup(Status.FOUND, area);
    }

    /** The point is in no known city. */
    public static CityLookup outside() {
        return new CityLookup(Status.OUTSIDE, null);
    }

    /** The answer is not known (Config unreachable, nothing cached). */
    public static CityLookup unknown() {
        return new CityLookup(Status.UNKNOWN, null);
    }

    /** True when a city was found. */
    public boolean isFound() {
        return status == Status.FOUND;
    }

    /** The city code when FOUND, else null. */
    public String cityCode() {
        return area == null ? null : area.code();
    }
}
