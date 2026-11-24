package com.heuristiq.configclient.city;

import java.util.List;

/**
 * One operating city with its service area, as served by Rider_ConfigService. The area is the drawn
 * {@code boundary} polygon when it has at least three points, otherwise the circle of {@code serviceRadiusKm}
 * around {@code center}.
 *
 * @param code            city code (upper case), the key of per-city settings
 * @param name            display name
 * @param tier            METRO, TIER_2, TIER_3, TOWN or null
 * @param timezone        IANA zone id of the city
 * @param serviceStatus   LIVE, PAUSED or COMING_SOON
 * @param center          map centre, null when none was set
 * @param serviceRadiusKm radius of the fallback circle, null when none was set
 * @param boundary        polygon vertices (never null, empty when none was drawn)
 * @param tenantId        owning organization, null for a city shared by every organization
 * @param locationId      the city's place in Rider_FranchiseService's location reference, null when not linked; it decides
 *                        which franchise covers a sale in the city
 */
public record ServiceArea(String code, String name, String tier, String timezone, String serviceStatus,
                          GeoPoint center, Double serviceRadiusKm, List<GeoPoint> boundary, String tenantId, String locationId) {

    public ServiceArea {
        boundary = boundary == null ? List.of() : List.copyOf(boundary);
    }

    /** A city not linked to a place (no {@code locationId}). */
    public ServiceArea(String code, String name, String tier, String timezone, String serviceStatus,
                       GeoPoint center, Double serviceRadiusKm, List<GeoPoint> boundary, String tenantId) {
        this(code, name, tier, timezone, serviceStatus, center, serviceRadiusKm, boundary, tenantId, null);
    }

    /** True when the drawn polygon defines the area. */
    public boolean hasBoundary() {
        return boundary.size() >= CityConstants.MIN_POLYGON_POINTS;
    }

    /** True when the centre and radius define a fallback circle. */
    public boolean hasCircle() {
        return center != null && serviceRadiusKm != null && serviceRadiusKm > 0;
    }

    /** True when the point lies in the area (polygon when present, else circle); false when the city has no area. */
    public boolean contains(double lat, double lng) {
        GeoPoint p = new GeoPoint(lat, lng);
        if (hasBoundary()) {
            return GeoMath.pointInPolygon(p, boundary);
        }
        return hasCircle() && GeoMath.haversineKm(center, p) <= serviceRadiusKm;
    }

    /** How far (km) the point lies outside the area; 0 when inside, or when the city has no area at all. */
    public double distanceOutsideKm(double lat, double lng) {
        GeoPoint p = new GeoPoint(lat, lng);
        if (hasBoundary()) {
            return GeoMath.distanceToPolygonKm(p, boundary);
        }
        if (hasCircle()) {
            return Math.max(0.0, GeoMath.haversineKm(center, p) - serviceRadiusKm);
        }
        return 0.0;
    }

    /** True when the city takes bookings ({@code serviceStatus} LIVE). */
    public boolean isLive() {
        return CityConstants.STATUS_LIVE.equalsIgnoreCase(serviceStatus);
    }

    /** True when the city belongs to one organization rather than being shared. */
    public boolean isOwnedBy(String organization) {
        return tenantId != null && tenantId.equals(organization);
    }
}
