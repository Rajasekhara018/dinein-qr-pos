package com.heuristiq.configclient.city;

/**
 * A point on the earth in decimal degrees (WGS84).
 *
 * @param lat latitude, -90..90
 * @param lng longitude, -180..180
 */
public record GeoPoint(double lat, double lng) {

    /** True when both coordinates are finite numbers inside their valid ranges. */
    public boolean isValid() {
        return Double.isFinite(lat) && Double.isFinite(lng)
                && lat >= CityConstants.MIN_LAT && lat <= CityConstants.MAX_LAT
                && lng >= CityConstants.MIN_LNG && lng <= CityConstants.MAX_LNG;
    }
}
