package com.heuristiq.configclient.city;

import java.util.ArrayList;
import java.util.List;

/**
 * Small, dependency-free geometry for service areas: great-circle distance, point-in-polygon (a point on an edge
 * counts as inside), distance from a point to a polygon and a circle approximated as a polygon. Polygons are lists
 * of vertices in order (closing vertex optional) and must not cross the antimeridian.
 */
public final class GeoMath {

    private GeoMath() { }

    /** Great-circle distance between two points in km (haversine). */
    public static double haversineKm(GeoPoint a, GeoPoint b) {
        return haversineKm(a.lat(), a.lng(), b.lat(), b.lng());
    }

    /** Great-circle distance between two coordinates in km (haversine). */
    public static double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * CityConstants.EARTH_RADIUS_KM * Math.asin(Math.min(1.0, Math.sqrt(h)));
    }

    /**
     * True when {@code p} lies inside {@code polygon} or on one of its edges (ray casting). A polygon with fewer than
     * three vertices contains nothing.
     */
    public static boolean pointInPolygon(GeoPoint p, List<GeoPoint> polygon) {
        if (p == null || polygon == null || polygon.size() < CityConstants.MIN_POLYGON_POINTS) {
            return false;
        }
        int n = polygon.size();
        boolean inside = false;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            GeoPoint a = polygon.get(i);
            GeoPoint b = polygon.get(j);
            if (onSegment(p, a, b)) {
                return true;
            }
            boolean crosses = (a.lat() > p.lat()) != (b.lat() > p.lat())
                    && p.lng() < (b.lng() - a.lng()) * (p.lat() - a.lat()) / (b.lat() - a.lat()) + a.lng();
            if (crosses) {
                inside = !inside;
            }
        }
        return inside;
    }

    /**
     * Distance in km from {@code p} to the nearest edge of {@code polygon}; 0 when the point is inside or on an edge.
     * Uses a local flat projection around {@code p}, accurate to well under 1% at city scale.
     */
    public static double distanceToPolygonKm(GeoPoint p, List<GeoPoint> polygon) {
        if (polygon == null || polygon.isEmpty()) {
            return 0.0;
        }
        if (pointInPolygon(p, polygon)) {
            return 0.0;
        }
        if (polygon.size() == 1) {
            return haversineKm(p, polygon.get(0));
        }
        double kmPerDegLat = Math.toRadians(1) * CityConstants.EARTH_RADIUS_KM;
        double kmPerDegLng = kmPerDegLat * Math.cos(Math.toRadians(p.lat()));
        double best = Double.MAX_VALUE;
        int n = polygon.size();
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double ax = (polygon.get(j).lng() - p.lng()) * kmPerDegLng;
            double ay = (polygon.get(j).lat() - p.lat()) * kmPerDegLat;
            double bx = (polygon.get(i).lng() - p.lng()) * kmPerDegLng;
            double by = (polygon.get(i).lat() - p.lat()) * kmPerDegLat;
            best = Math.min(best, distanceFromOriginToSegment(ax, ay, bx, by));
        }
        return best;
    }

    /**
     * A circle as a polygon of {@code points} vertices (at least 3), each exactly {@code radiusKm} from
     * {@code center} along a great circle, starting due north and going clockwise.
     */
    public static List<GeoPoint> circleAsPolygon(GeoPoint center, double radiusKm, int points) {
        if (points < CityConstants.MIN_POLYGON_POINTS) {
            throw new IllegalArgumentException("a polygon needs at least 3 points");
        }
        double angular = radiusKm / CityConstants.EARTH_RADIUS_KM;
        double lat1 = Math.toRadians(center.lat());
        double lng1 = Math.toRadians(center.lng());
        List<GeoPoint> result = new ArrayList<>(points);
        for (int k = 0; k < points; k++) {
            double bearing = 2 * Math.PI * k / points;
            double lat2 = Math.asin(Math.sin(lat1) * Math.cos(angular)
                    + Math.cos(lat1) * Math.sin(angular) * Math.cos(bearing));
            double lng2 = lng1 + Math.atan2(Math.sin(bearing) * Math.sin(angular) * Math.cos(lat1),
                    Math.cos(angular) - Math.sin(lat1) * Math.sin(lat2));
            result.add(new GeoPoint(Math.toDegrees(lat2), Math.toDegrees(lng2)));
        }
        return result;
    }

    /**
     * True when two polygons share area. Polygons that only touch (a shared border, a common vertex) do not overlap,
     * so neighbouring cities may be drawn edge to edge. Detected when an edge of one properly crosses an edge of the
     * other, or a vertex or the vertex centroid of one lies strictly inside the other (this also covers one polygon
     * wholly inside the other and two identical polygons).
     */
    public static boolean polygonsOverlap(List<GeoPoint> a, List<GeoPoint> b) {
        if (a == null || b == null || a.size() < CityConstants.MIN_POLYGON_POINTS
                || b.size() < CityConstants.MIN_POLYGON_POINTS) {
            return false;
        }
        for (int i = 0, j = a.size() - 1; i < a.size(); j = i++) {
            for (int k = 0, l = b.size() - 1; k < b.size(); l = k++) {
                if (segmentsCrossProperly(a.get(j), a.get(i), b.get(l), b.get(k))) {
                    return true;
                }
            }
        }
        return anyStrictlyInside(a, b) || anyStrictlyInside(b, a)
                || strictlyInside(centroid(a), b) || strictlyInside(centroid(b), a);
    }

    private static boolean anyStrictlyInside(List<GeoPoint> points, List<GeoPoint> polygon) {
        for (GeoPoint p : points) {
            if (strictlyInside(p, polygon)) {
                return true;
            }
        }
        return false;
    }

    private static boolean strictlyInside(GeoPoint p, List<GeoPoint> polygon) {
        if (!pointInPolygon(p, polygon)) {
            return false;
        }
        for (int i = 0, j = polygon.size() - 1; i < polygon.size(); j = i++) {
            if (onSegment(p, polygon.get(i), polygon.get(j))) {
                return false;
            }
        }
        return true;
    }

    private static GeoPoint centroid(List<GeoPoint> polygon) {
        double lat = 0;
        double lng = 0;
        for (GeoPoint p : polygon) {
            lat += p.lat();
            lng += p.lng();
        }
        return new GeoPoint(lat / polygon.size(), lng / polygon.size());
    }

    private static boolean onSegment(GeoPoint p, GeoPoint a, GeoPoint b) {
        double cross = (b.lng() - a.lng()) * (p.lat() - a.lat()) - (b.lat() - a.lat()) * (p.lng() - a.lng());
        double length = Math.hypot(b.lng() - a.lng(), b.lat() - a.lat());
        if (Math.abs(cross) > CityConstants.EDGE_EPSILON_DEG * Math.max(length, 1.0)) {
            return false;
        }
        return p.lng() >= Math.min(a.lng(), b.lng()) - CityConstants.EDGE_EPSILON_DEG
                && p.lng() <= Math.max(a.lng(), b.lng()) + CityConstants.EDGE_EPSILON_DEG
                && p.lat() >= Math.min(a.lat(), b.lat()) - CityConstants.EDGE_EPSILON_DEG
                && p.lat() <= Math.max(a.lat(), b.lat()) + CityConstants.EDGE_EPSILON_DEG;
    }

    private static boolean segmentsCrossProperly(GeoPoint p1, GeoPoint p2, GeoPoint q1, GeoPoint q2) {
        double d1 = orientation(q1, q2, p1);
        double d2 = orientation(q1, q2, p2);
        double d3 = orientation(p1, p2, q1);
        double d4 = orientation(p1, p2, q2);
        return oppositeSides(d1, d2) && oppositeSides(d3, d4);
    }

    /** Strictly opposite signs, treating floating noise around zero (collinear or touching) as zero. */
    private static boolean oppositeSides(double a, double b) {
        double eps = CityConstants.ORIENTATION_EPSILON;
        return (a > eps && b < -eps) || (a < -eps && b > eps);
    }

    private static double orientation(GeoPoint a, GeoPoint b, GeoPoint c) {
        return (b.lng() - a.lng()) * (c.lat() - a.lat()) - (b.lat() - a.lat()) * (c.lng() - a.lng());
    }

    private static double distanceFromOriginToSegment(double ax, double ay, double bx, double by) {
        double dx = bx - ax;
        double dy = by - ay;
        double lengthSq = dx * dx + dy * dy;
        double t = lengthSq == 0 ? 0 : Math.max(0, Math.min(1, -(ax * dx + ay * dy) / lengthSq));
        return Math.hypot(ax + t * dx, ay + t * dy);
    }
}
