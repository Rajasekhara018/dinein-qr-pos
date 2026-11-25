package com.heuristiq.configclient.city;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.Test;

class GeoMathTest {

    /** Roughly a 10 km x 10 km square around Hyderabad (lat 17.30..17.39, lng 78.40..78.49). */
    private static final List<GeoPoint> SQUARE = List.of(
            new GeoPoint(17.30, 78.40), new GeoPoint(17.30, 78.49),
            new GeoPoint(17.39, 78.49), new GeoPoint(17.39, 78.40));

    /** U-shaped (concave) polygon: the notch between the arms (lat 1..3, lng 1..2) is outside. */
    private static final List<GeoPoint> U_SHAPE = List.of(
            new GeoPoint(0, 0), new GeoPoint(0, 3), new GeoPoint(3, 3), new GeoPoint(3, 2),
            new GeoPoint(1, 2), new GeoPoint(1, 1), new GeoPoint(3, 1), new GeoPoint(3, 0));

    @Test
    void haversineMatchesKnownDistances() {
        // Hyderabad (Charminar) to Vijayawada is about 250-270 km great-circle
        double d = GeoMath.haversineKm(new GeoPoint(17.3616, 78.4747), new GeoPoint(16.5062, 80.6480));
        assertThat(d).isBetween(245.0, 260.0);
        // one degree of latitude is about 111.2 km
        assertThat(GeoMath.haversineKm(0, 0, 1, 0)).isCloseTo(111.2, within(0.1));
        assertThat(GeoMath.haversineKm(10, 10, 10, 10)).isZero();
    }

    @Test
    void pointInsideOutsideAndOnTheEdgeOfAConvexPolygon() {
        assertThat(GeoMath.pointInPolygon(new GeoPoint(17.35, 78.45), SQUARE)).isTrue();
        assertThat(GeoMath.pointInPolygon(new GeoPoint(17.45, 78.45), SQUARE)).isFalse();
        assertThat(GeoMath.pointInPolygon(new GeoPoint(17.35, 78.39), SQUARE)).isFalse();
        // exactly on an edge and on a vertex count as inside
        assertThat(GeoMath.pointInPolygon(new GeoPoint(17.30, 78.45), SQUARE)).isTrue();
        assertThat(GeoMath.pointInPolygon(new GeoPoint(17.35, 78.49), SQUARE)).isTrue();
        assertThat(GeoMath.pointInPolygon(new GeoPoint(17.39, 78.40), SQUARE)).isTrue();
    }

    @Test
    void concavePolygonExcludesItsNotch() {
        assertThat(GeoMath.pointInPolygon(new GeoPoint(0.5, 1.5), U_SHAPE)).isTrue();   // base of the U
        assertThat(GeoMath.pointInPolygon(new GeoPoint(2, 0.5), U_SHAPE)).isTrue();     // left arm
        assertThat(GeoMath.pointInPolygon(new GeoPoint(2, 2.5), U_SHAPE)).isTrue();     // right arm
        assertThat(GeoMath.pointInPolygon(new GeoPoint(2, 1.5), U_SHAPE)).isFalse();    // the notch
    }

    @Test
    void degeneratePolygonsContainNothing() {
        assertThat(GeoMath.pointInPolygon(new GeoPoint(0, 0), List.of(new GeoPoint(0, 0), new GeoPoint(1, 1)))).isFalse();
        assertThat(GeoMath.pointInPolygon(new GeoPoint(0, 0), null)).isFalse();
        assertThat(GeoMath.pointInPolygon(null, SQUARE)).isFalse();
    }

    @Test
    void distanceToPolygonIsZeroInsideAndTheGapOutside() {
        assertThat(GeoMath.distanceToPolygonKm(new GeoPoint(17.35, 78.45), SQUARE)).isZero();
        // 0.01 degree of latitude north of the top edge is about 1.11 km
        assertThat(GeoMath.distanceToPolygonKm(new GeoPoint(17.40, 78.45), SQUARE)).isCloseTo(1.11, within(0.02));
        // diagonal from the corner: about sqrt(1.11^2 + 1.06^2)
        double corner = GeoMath.distanceToPolygonKm(new GeoPoint(17.40, 78.50), SQUARE);
        assertThat(corner).isCloseTo(GeoMath.haversineKm(new GeoPoint(17.40, 78.50), new GeoPoint(17.39, 78.49)), within(0.02));
        // the notch of a concave polygon is outside, at 0.5 degree from the arms
        assertThat(GeoMath.distanceToPolygonKm(new GeoPoint(2, 1.5), U_SHAPE)).isCloseTo(55.6, within(0.5));
        assertThat(GeoMath.distanceToPolygonKm(new GeoPoint(1, 1), List.of())).isZero();
        assertThat(GeoMath.distanceToPolygonKm(new GeoPoint(0, 1), List.of(new GeoPoint(0, 0))))
                .isCloseTo(111.2, within(0.2));
    }

    @Test
    void circleAsPolygonPutsEveryVertexOnTheRadius() {
        GeoPoint center = new GeoPoint(16.3067, 80.4365);
        List<GeoPoint> circle = GeoMath.circleAsPolygon(center, 12.0, 32);
        assertThat(circle).hasSize(32);
        assertThat(circle).allSatisfy(p -> assertThat(GeoMath.haversineKm(center, p)).isCloseTo(12.0, within(0.01)));
        assertThat(circle.get(0).lat()).isGreaterThan(center.lat()); // starts due north
        assertThat(GeoMath.pointInPolygon(center, circle)).isTrue();
        assertThatThrownBy(() -> GeoMath.circleAsPolygon(center, 1, 2)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void overlapDetectsCrossingContainedAndIdenticalPolygons() {
        List<GeoPoint> shifted = List.of(new GeoPoint(17.35, 78.45), new GeoPoint(17.35, 78.55),
                new GeoPoint(17.45, 78.55), new GeoPoint(17.45, 78.45));
        List<GeoPoint> inner = List.of(new GeoPoint(17.33, 78.43), new GeoPoint(17.33, 78.46),
                new GeoPoint(17.36, 78.46), new GeoPoint(17.36, 78.43));
        assertThat(GeoMath.polygonsOverlap(SQUARE, shifted)).isTrue();
        assertThat(GeoMath.polygonsOverlap(SQUARE, inner)).isTrue();
        assertThat(GeoMath.polygonsOverlap(inner, SQUARE)).isTrue();
        assertThat(GeoMath.polygonsOverlap(SQUARE, SQUARE)).isTrue();
    }

    @Test
    void neighboursThatOnlyShareABorderOrAreApartDoNotOverlap() {
        List<GeoPoint> eastNeighbour = List.of(new GeoPoint(17.30, 78.49), new GeoPoint(17.30, 78.58),
                new GeoPoint(17.39, 78.58), new GeoPoint(17.39, 78.49));
        List<GeoPoint> far = List.of(new GeoPoint(16.0, 80.0), new GeoPoint(16.0, 80.1), new GeoPoint(16.1, 80.1));
        assertThat(GeoMath.polygonsOverlap(SQUARE, eastNeighbour)).isFalse();
        assertThat(GeoMath.polygonsOverlap(SQUARE, far)).isFalse();
        assertThat(GeoMath.polygonsOverlap(SQUARE, List.of(new GeoPoint(17.35, 78.45)))).isFalse();
        assertThat(GeoMath.polygonsOverlap(null, SQUARE)).isFalse();
    }

    @Test
    void geoPointValidity() {
        assertThat(new GeoPoint(17.3, 78.4).isValid()).isTrue();
        assertThat(new GeoPoint(91, 0).isValid()).isFalse();
        assertThat(new GeoPoint(0, -181).isValid()).isFalse();
        assertThat(new GeoPoint(Double.NaN, 0).isValid()).isFalse();
        assertThat(new GeoPoint(0, Double.POSITIVE_INFINITY).isValid()).isFalse();
    }
}
