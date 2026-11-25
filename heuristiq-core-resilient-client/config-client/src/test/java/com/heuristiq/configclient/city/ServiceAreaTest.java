package com.heuristiq.configclient.city;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ServiceAreaTest {

    private static final GeoPoint GUNTUR = new GeoPoint(16.3067, 80.4365);
    private static final List<GeoPoint> SQUARE = List.of(
            new GeoPoint(17.30, 78.40), new GeoPoint(17.30, 78.49),
            new GeoPoint(17.39, 78.49), new GeoPoint(17.39, 78.40));

    private static ServiceArea circle(String status) {
        return new ServiceArea("GNT", "Guntur", "TIER_2", "Asia/Kolkata", status, GUNTUR, 10.0, null, null);
    }

    @Test
    void circleAreaContainsPointsWithinTheRadius() {
        ServiceArea a = circle("LIVE");
        assertThat(a.hasBoundary()).isFalse();
        assertThat(a.hasCircle()).isTrue();
        assertThat(a.boundary()).isEmpty();
        assertThat(a.contains(GUNTUR.lat(), GUNTUR.lng())).isTrue();
        assertThat(a.contains(GUNTUR.lat() + 0.05, GUNTUR.lng())).isTrue();   // ~5.6 km
        assertThat(a.contains(GUNTUR.lat() + 0.10, GUNTUR.lng())).isFalse();  // ~11.1 km
        assertThat(a.distanceOutsideKm(GUNTUR.lat() + 0.05, GUNTUR.lng())).isZero();
        assertThat(a.distanceOutsideKm(GUNTUR.lat() + 0.10, GUNTUR.lng())).isCloseTo(1.12, within(0.02));
    }

    @Test
    void boundaryWinsOverTheCircle() {
        // centre/radius around Guntur, but the drawn polygon is in Hyderabad: only the polygon counts
        ServiceArea a = new ServiceArea("HYD", "Hyderabad", "METRO", "Asia/Kolkata", "LIVE", GUNTUR, 50.0, SQUARE, "org-a");
        assertThat(a.hasBoundary()).isTrue();
        assertThat(a.contains(17.35, 78.45)).isTrue();
        assertThat(a.contains(GUNTUR.lat(), GUNTUR.lng())).isFalse();
        assertThat(a.distanceOutsideKm(17.35, 78.45)).isZero();
        assertThat(a.distanceOutsideKm(17.40, 78.45)).isCloseTo(1.11, within(0.02));
    }

    @Test
    void cityWithoutGeometryContainsNothingAndHasNoDistance() {
        ServiceArea a = new ServiceArea("X", "X", null, null, "LIVE", null, null, List.of(new GeoPoint(1, 1)), null);
        assertThat(a.hasBoundary()).isFalse();
        assertThat(a.hasCircle()).isFalse();
        assertThat(a.contains(1, 1)).isFalse();
        assertThat(a.distanceOutsideKm(1, 1)).isZero();
        ServiceArea zeroRadius = new ServiceArea("Y", "Y", null, null, "LIVE", GUNTUR, 0.0, null, null);
        assertThat(zeroRadius.hasCircle()).isFalse();
    }

    @Test
    void liveAndOwnership() {
        assertThat(circle("LIVE").isLive()).isTrue();
        assertThat(circle("live").isLive()).isTrue();
        assertThat(circle("PAUSED").isLive()).isFalse();
        assertThat(circle(null).isLive()).isFalse();
        ServiceArea own = new ServiceArea("A", "A", null, null, "LIVE", GUNTUR, 1.0, null, "org-a");
        assertThat(own.isOwnedBy("org-a")).isTrue();
        assertThat(own.isOwnedBy("org-b")).isFalse();
        assertThat(circle("LIVE").isOwnedBy("org-a")).isFalse();
        assertThat(circle("LIVE").isOwnedBy(null)).isFalse();
    }

    @Test
    void boundaryIsAnImmutableCopy() {
        List<GeoPoint> points = new ArrayList<>(SQUARE);
        ServiceArea a = new ServiceArea("HYD", "Hyderabad", null, null, "LIVE", null, null, points, null);
        points.clear();
        assertThat(a.boundary()).hasSize(4);
        assertThatThrownBy(() -> a.boundary().add(GUNTUR)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void cityLookupFactoriesAndInvariants() {
        ServiceArea a = circle("LIVE");
        assertThat(CityLookup.found(a).isFound()).isTrue();
        assertThat(CityLookup.found(a).cityCode()).isEqualTo("GNT");
        assertThat(CityLookup.outside().status()).isEqualTo(CityLookup.Status.OUTSIDE);
        assertThat(CityLookup.outside().cityCode()).isNull();
        assertThat(CityLookup.unknown().status()).isEqualTo(CityLookup.Status.UNKNOWN);
        assertThat(CityLookup.unknown().isFound()).isFalse();
        assertThatThrownBy(() -> new CityLookup(null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CityLookup(CityLookup.Status.FOUND, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CityLookup(CityLookup.Status.OUTSIDE, a)).isInstanceOf(IllegalArgumentException.class);
    }
}
