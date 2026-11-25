package com.heuristiq.configclient.city;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heuristiq.configclient.ConfigClientConstants;
import com.heuristiq.configclient.ConfigClientOptions;
import com.heuristiq.resilient.ResilienceProfile;
import com.heuristiq.resilient.ResilientCaller;
import com.heuristiq.resilient.TenantIdPropagation;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

class CityLocatorTest {

    private static final class TestClock extends Clock {
        private Instant now = Instant.parse("2026-11-04T00:00:00Z");

        void advance(Duration d) { now = now.plus(d); }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    /** Fake Config: answers per X-Tenant-Id ("" = no header), records requests. */
    private static final class Fake {
        final List<ClientRequest> requests = new CopyOnWriteArrayList<>();
        final Map<String, String> bodyByTenant = new ConcurrentHashMap<>();
        volatile boolean down;

        Mono<ClientResponse> handle(ClientRequest req) {
            requests.add(req);
            if (down) {
                return Mono.just(ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE).build());
            }
            String tenant = req.headers().getFirst(TenantIdPropagation.HEADER);
            String body = bodyByTenant.getOrDefault(tenant == null ? "" : tenant, envelope());
            return Mono.just(ClientResponse.create(HttpStatus.OK, ExchangeStrategies.withDefaults())
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE).body(body).build());
        }
    }

    private static final String HYD_SHARED = area("HYD", "LIVE", null,
            "\"centerLat\":17.385,\"centerLng\":78.4867,\"serviceRadiusKm\":25");
    private static final String HYD_ORG_A_POLYGON = area("HYDA", "LIVE", "org-a",
            "\"boundary\":[[17.30,78.40],[17.30,78.55],[17.45,78.55],[17.45,78.40]]");
    private static final String GNT_ORG_B = area("GNT", "LIVE", "org-b",
            "\"centerLat\":16.3067,\"centerLng\":80.4365,\"serviceRadiusKm\":12");
    private static final String PAUSED_SHARED = area("OLDHYD", "PAUSED", null,
            "\"centerLat\":17.385,\"centerLng\":78.4867,\"serviceRadiusKm\":30");

    private static String area(String code, String status, String tenant, String geometry) {
        return "{\"code\":\"" + code + "\",\"name\":\"" + code + " city\",\"tier\":\"METRO\",\"timezone\":\"Asia/Kolkata\","
                + "\"serviceStatus\":\"" + status + "\",\"tenantId\":" + (tenant == null ? "null" : "\"" + tenant + "\"")
                + "," + geometry + "}";
    }

    private static String envelope(String... areas) {
        return "{\"success\":true,\"responseObject\":[" + String.join(",", areas) + "]}";
    }

    private final Fake fake = new Fake();
    private final TestClock clock = new TestClock();
    private final ConfigClientOptions options = new ConfigClientOptions("tok", Duration.ofSeconds(30), 100,
            Duration.ofMinutes(10));
    private final CityLocator locator = new CityLocator(
            WebClient.builder().baseUrl("http://config/api/v1/config").exchangeFunction(fake::handle).build(),
            new ResilientCaller("config-city-test", ResilienceProfile.defaults().withMaxAttempts(1)), options, clock);

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void findsTheCityAndCallsTheServiceAreasEndpointWithTokenAndOrganization() {
        fake.bodyByTenant.put("org-b", envelope(HYD_SHARED, GNT_ORG_B));

        CityLookup lookup = locator.locate(16.31, 80.44, "org-b");

        assertThat(lookup.status()).isEqualTo(CityLookup.Status.FOUND);
        assertThat(lookup.cityCode()).isEqualTo("GNT");
        assertThat(lookup.area().tenantId()).isEqualTo("org-b");
        assertThat(lookup.area().timezone()).isEqualTo("Asia/Kolkata");
        ClientRequest req = fake.requests.get(0);
        assertThat(req.url().getPath()).isEqualTo("/api/v1/config" + CityConstants.SERVICE_AREAS_PATH);
        assertThat(req.headers().getFirst(ConfigClientConstants.SERVICE_TOKEN_HEADER)).isEqualTo("tok");
        assertThat(req.headers().getFirst(TenantIdPropagation.HEADER)).isEqualTo("org-b");
    }

    @Test
    void pointInNoCityIsOutside() {
        fake.bodyByTenant.put("org-b", envelope(HYD_SHARED, GNT_ORG_B));
        assertThat(locator.locate(28.61, 77.20, "org-b").status()).isEqualTo(CityLookup.Status.OUTSIDE);
        fake.bodyByTenant.put("org-c", envelope());
        assertThat(locator.locate(17.385, 78.4867, "org-c").status()).isEqualTo(CityLookup.Status.OUTSIDE);
    }

    @Test
    void listIsCachedUntilThePollIntervalThenRefreshed() {
        fake.bodyByTenant.put("org-b", envelope(GNT_ORG_B));
        locator.locate(16.31, 80.44, "org-b");
        locator.locate(16.31, 80.44, "org-b");
        locator.byCode("gnt", "org-b");
        locator.areas("org-b");
        assertThat(fake.requests).hasSize(1);

        clock.advance(Duration.ofSeconds(31));
        fake.bodyByTenant.put("org-b", envelope());
        assertThat(locator.locate(16.31, 80.44, "org-b").status()).isEqualTo(CityLookup.Status.OUTSIDE);
        assertThat(fake.requests).hasSize(2);
    }

    @Test
    void organizationsHaveSeparateListsAndNeverSeeEachOthersCities() {
        fake.bodyByTenant.put("org-a", envelope(HYD_SHARED, HYD_ORG_A_POLYGON));
        fake.bodyByTenant.put("org-b", envelope(HYD_SHARED, GNT_ORG_B));

        assertThat(locator.locate(16.31, 80.44, "org-a").status()).isEqualTo(CityLookup.Status.OUTSIDE);
        assertThat(locator.locate(16.31, 80.44, "org-b").cityCode()).isEqualTo("GNT");
        assertThat(locator.byCode("GNT", "org-a")).isEmpty();
        assertThat(locator.byCode("HYDA", "org-b")).isEmpty();
        assertThat(locator.areas("org-a")).extracting(ServiceArea::code).containsExactly("HYD", "HYDA");
        assertThat(locator.cachedOrganizations()).isEqualTo(2);
    }

    @Test
    void ownOrganizationCityWinsOverASharedCityContainingThePoint() {
        // shared HYD (circle) listed first; org-a's own polygon also contains the point
        fake.bodyByTenant.put("org-a", envelope(HYD_SHARED, HYD_ORG_A_POLYGON));
        assertThat(locator.locate(17.385, 78.4867, "org-a").cityCode()).isEqualTo("HYDA");
        // outside org-a's polygon but inside the shared circle: shared city
        assertThat(locator.locate(17.55, 78.4867, "org-a").cityCode()).isEqualTo("HYD");
    }

    @Test
    void liveCityWinsOverAPausedOneOfTheSameOwner() {
        fake.bodyByTenant.put("", envelope(PAUSED_SHARED, HYD_SHARED));
        assertThat(locator.locate(17.385, 78.4867, null).cityCode()).isEqualTo("HYD");
        // only the paused one contains this point: still FOUND, caller checks isLive()
        CityLookup paused = locator.locate(17.385 + 0.25, 78.4867, null);
        assertThat(paused.cityCode()).isEqualTo("OLDHYD");
        assertThat(paused.area().isLive()).isFalse();
    }

    @Test
    void byCodePrefersOwnCityAndIsCaseInsensitive() {
        String ownHyd = area("HYD", "PAUSED", "org-a", "\"centerLat\":17.0,\"centerLng\":78.0,\"serviceRadiusKm\":5");
        fake.bodyByTenant.put("org-a", envelope(HYD_SHARED, ownHyd));
        assertThat(locator.byCode(" hyd ", "org-a")).get().extracting(ServiceArea::tenantId).isEqualTo("org-a");
        assertThat(locator.byCode("nope", "org-a")).isEmpty();
        assertThat(locator.byCode(null, "org-a")).isEmpty();
        assertThat(locator.byCode(" ", "org-a")).isEmpty();
    }

    @Test
    void outageServesTheLastKnownListThenAnswersUnknown() {
        fake.bodyByTenant.put("org-b", envelope(GNT_ORG_B));
        assertThat(locator.locate(16.31, 80.44, "org-b").isFound()).isTrue();

        fake.down = true;
        clock.advance(Duration.ofMinutes(5)); // past pollInterval, within staleTtl
        assertThat(locator.locate(16.31, 80.44, "org-b").cityCode()).isEqualTo("GNT");
        assertThat(locator.areas("org-b")).hasSize(1);

        clock.advance(Duration.ofMinutes(6)); // 11 minutes after the last fetch: past staleTtl
        assertThat(locator.locate(16.31, 80.44, "org-b").status()).isEqualTo(CityLookup.Status.UNKNOWN);
        assertThat(locator.areas("org-b")).isEmpty();
        assertThat(locator.byCode("GNT", "org-b")).isEmpty();

        fake.down = false;
        assertThat(locator.locate(16.31, 80.44, "org-b").cityCode()).isEqualTo("GNT");
    }

    @Test
    void outageWithNothingCachedIsUnknownNeverAnException() {
        fake.down = true;
        assertThat(locator.locate(16.31, 80.44, "org-b").status()).isEqualTo(CityLookup.Status.UNKNOWN);
        assertThat(locator.areas("org-b")).isEmpty();
    }

    @Test
    void malformedResponseIsTreatedAsAnOutage() {
        fake.bodyByTenant.put("org-b", "{\"success\":true,\"responseObject\":{\"not\":\"a list\"}}");
        assertThat(locator.locate(16.31, 80.44, "org-b").status()).isEqualTo(CityLookup.Status.UNKNOWN);
        fake.bodyByTenant.put("org-c", "not json");
        assertThat(locator.locate(16.31, 80.44, "org-c").status()).isEqualTo(CityLookup.Status.UNKNOWN);
    }

    @Test
    void nonFiniteCoordinatesAreUnknownWithoutACall() {
        assertThat(locator.locate(Double.NaN, 80.0, "org-b").status()).isEqualTo(CityLookup.Status.UNKNOWN);
        assertThat(locator.locate(16.0, Double.POSITIVE_INFINITY, "org-b").status()).isEqualTo(CityLookup.Status.UNKNOWN);
        assertThat(fake.requests).isEmpty();
    }

    @Test
    void blankTenantUsesTheRequestOrganizationFromMdcElseSharedOnly() {
        fake.bodyByTenant.put("org-b", envelope(GNT_ORG_B));
        fake.bodyByTenant.put("", envelope(HYD_SHARED));

        assertThat(locator.locate(16.31, 80.44, null).status()).isEqualTo(CityLookup.Status.OUTSIDE);
        assertThat(fake.requests.get(0).headers().getFirst(TenantIdPropagation.HEADER)).isNull();

        MDC.put(TenantIdPropagation.MDC_KEY, "org-b");
        assertThat(locator.locate(16.31, 80.44, " ").cityCode()).isEqualTo("GNT");
        assertThat(fake.requests.get(1).headers().getFirst(TenantIdPropagation.HEADER)).isEqualTo("org-b");
        // an explicit organization wins over the MDC one
        fake.bodyByTenant.put("org-x", envelope());
        assertThat(locator.locate(16.31, 80.44, "org-x").status()).isEqualTo(CityLookup.Status.OUTSIDE);
    }

    @Test
    void entriesWithoutCodeOrGeometryOrWithBadPointsAreSkipped() throws Exception {
        ObjectMapper m = new ObjectMapper();
        assertThat(CityLocator.toArea(m.readTree("{\"name\":\"x\",\"centerLat\":1,\"centerLng\":1,\"serviceRadiusKm\":5}")))
                .isNull();
        assertThat(CityLocator.toArea(m.readTree("{\"code\":\"x\"}"))).isNull();
        assertThat(CityLocator.toArea(m.readTree("{\"code\":\"x\",\"centerLat\":95,\"centerLng\":1,\"serviceRadiusKm\":5}")))
                .isNull();
        ServiceArea a = CityLocator.toArea(m.readTree(
                "{\"code\":\"vjw\",\"boundary\":[[16.5,80.6],[16.5,\"x\"],[16.6],[16.6,80.7],[16.6,80.6],[100,1]]}"));
        assertThat(a.code()).isEqualTo("VJW");
        assertThat(a.boundary()).hasSize(3);
        assertThat(a.tenantId()).isNull();
        assertThat(a.center()).isNull();
        assertThat(a.locationId()).isNull();
    }

    @Test
    void theCitysPlaceIsReadWhenConfigLinksOne() throws Exception {
        ObjectMapper m = new ObjectMapper();

        ServiceArea linked = CityLocator.toArea(m.readTree(
                "{\"code\":\"vjw\",\"centerLat\":16.5,\"centerLng\":80.6,\"serviceRadiusKm\":10,\"locationId\":\" loc-vjw \"}"));
        ServiceArea blank = CityLocator.toArea(m.readTree(
                "{\"code\":\"gnt\",\"centerLat\":16.3,\"centerLng\":80.4,\"serviceRadiusKm\":10,\"locationId\":\"  \"}"));

        assertThat(linked.locationId()).isEqualTo("loc-vjw");
        assertThat(blank.locationId()).isNull();
    }
}
