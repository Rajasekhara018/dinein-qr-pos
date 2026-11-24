package com.heuristiq.configclient.vehicle;

import static org.assertj.core.api.Assertions.assertThat;

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

class VehicleTypeCatalogueTest {

    private static final class TestClock extends Clock {
        private Instant now = Instant.parse("2026-11-06T00:00:00Z");

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

    /** One entry as the endpoint sends it: the full compact view. */
    private static String type(String code, boolean active) {
        return "{\"code\":\"" + code + "\",\"name\":\"" + code + " name\",\"category\":\"FOUR_WHEELER\","
                + "\"passengerCapacity\":4,\"active\":" + active + ",\"availableInCity\":null}";
    }

    private static String envelope(String... entries) {
        return "{\"success\":true,\"responseObject\":[" + String.join(",", entries) + "]}";
    }

    private final Fake fake = new Fake();
    private final TestClock clock = new TestClock();
    private final ConfigClientOptions options = new ConfigClientOptions("tok", Duration.ofSeconds(30), 100,
            Duration.ofMinutes(10));
    private final VehicleTypeCatalogue catalogue = new VehicleTypeCatalogue(
            WebClient.builder().baseUrl("http://config/api/v1/config").exchangeFunction(fake::handle).build(),
            new ResilientCaller("config-vehicle-test", ResilienceProfile.defaults().withMaxAttempts(1)), options, clock);

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void readsTheCodesFromTheRichPayloadAndCallsTheEndpointWithTokenAndOrganization() {
        fake.bodyByTenant.put("org-a", envelope(type("BIKE", true), type("suv_xl", false)));

        assertThat(catalogue.codes("org-a")).contains(java.util.Set.of("BIKE", "SUV_XL"));

        ClientRequest req = fake.requests.get(0);
        assertThat(req.url().getPath()).isEqualTo("/api/v1/config" + VehicleTypeConstants.VEHICLE_TYPES_PATH);
        assertThat(req.headers().getFirst(ConfigClientConstants.SERVICE_TOKEN_HEADER)).isEqualTo("tok");
        assertThat(req.headers().getFirst(TenantIdPropagation.HEADER)).isEqualTo("org-a");
    }

    /** An entry with an explicit capacity (any JSON value, so a bad one can be sent). */
    private static String typeSeating(String code, String capacityJson) {
        return "{\"code\":\"" + code + "\",\"name\":\"n\",\"category\":\"FOUR_WHEELER\",\"passengerCapacity\":" + capacityJson
                + ",\"active\":true}";
    }

    @Test
    void readsHowManyPassengersEachTypeSeatsFromTheSameFetchAsTheCodes() {
        fake.bodyByTenant.put("org-a", envelope(typeSeating("BIKE", "1"), typeSeating("suv_xl", "6")));

        assertThat(catalogue.capacities("org-a")).contains(Map.of("BIKE", 1, "SUV_XL", 6));
        assertThat(catalogue.codes("org-a")).contains(java.util.Set.of("BIKE", "SUV_XL"));
        assertThat(fake.requests).hasSize(1);
    }

    @Test
    void aTypeWithoutAUsableCapacityIsInTheCodesButNotInTheCapacities() {
        fake.bodyByTenant.put("org-a", envelope(typeSeating("AUTO", "3"), typeSeating("ZERO", "0"), typeSeating("NEG", "-2"),
                typeSeating("TEXT", "\"4\""), typeSeating("FRAC", "2.5"), typeSeating("NUL", "null"), "{\"code\":\"BARE\"}", "\"PLAIN\""));

        assertThat(catalogue.capacities("org-a")).contains(Map.of("AUTO", 3));
        assertThat(catalogue.codes("org-a").orElseThrow())
                .containsExactlyInAnyOrder("AUTO", "ZERO", "NEG", "TEXT", "FRAC", "NUL", "BARE", "PLAIN");
    }

    @Test
    void capacitiesFollowTheOrganizationAndAreEmptyWhenConfigIsDownWithNoCopy() {
        fake.bodyByTenant.put("org-a", envelope(typeSeating("SUV_XL", "6")));
        fake.bodyByTenant.put("org-b", envelope(typeSeating("VAN_B", "8")));

        assertThat(catalogue.capacities("org-a").orElseThrow()).containsOnlyKeys("SUV_XL");
        assertThat(catalogue.capacities("org-b").orElseThrow()).containsOnlyKeys("VAN_B");

        fake.down = true;
        assertThat(catalogue.capacities("org-c")).isEmpty();
        // org-a was fetched earlier: the last copy is still served inside the outage window
        clock.advance(Duration.ofSeconds(31));
        assertThat(catalogue.capacities("org-a").orElseThrow()).containsEntry("SUV_XL", 6);
        clock.advance(Duration.ofMinutes(11));
        assertThat(catalogue.capacities("org-a")).isEmpty();
    }

    @Test
    void alsoAcceptsAPlainListOfCodesAndSkipsEntriesWithoutOne() {
        fake.bodyByTenant.put("org-a", envelope("\"BIKE\"", "{\"name\":\"no code\"}", "{\"code\":\" \"}", "42", type("AUTO", true)));

        assertThat(catalogue.codes("org-a")).contains(java.util.Set.of("BIKE", "AUTO"));
    }

    @Test
    void anEmptyCatalogueIsAnAnswerNotAnOutage() {
        assertThat(catalogue.codes("org-a")).contains(java.util.Set.of());
    }

    @Test
    void theSetIsCachedUntilThePollIntervalThenRefreshed() {
        fake.bodyByTenant.put("org-a", envelope(type("BIKE", true)));
        catalogue.codes("org-a");
        catalogue.codes("org-a");
        assertThat(fake.requests).hasSize(1);

        clock.advance(Duration.ofSeconds(31));
        fake.bodyByTenant.put("org-a", envelope(type("BIKE", true), type("AUTO", true)));
        assertThat(catalogue.codes("org-a")).contains(java.util.Set.of("BIKE", "AUTO"));
        assertThat(fake.requests).hasSize(2);
    }

    @Test
    void organizationsHaveSeparateSetsAndNeverSeeEachOthersTypes() {
        fake.bodyByTenant.put("org-a", envelope(type("BIKE", true), type("SUV_XL", true)));
        fake.bodyByTenant.put("org-b", envelope(type("BIKE", true), type("VAN_B", true)));

        assertThat(catalogue.codes("org-a").orElseThrow()).contains("SUV_XL").doesNotContain("VAN_B");
        assertThat(catalogue.codes("org-b").orElseThrow()).contains("VAN_B").doesNotContain("SUV_XL");
        assertThat(catalogue.cachedOrganizations()).isEqualTo(2);
    }

    @Test
    void aCallerWithoutAnOrganizationUsesTheRequestsOrganizationOtherwiseTheSharedSet() {
        fake.bodyByTenant.put("org-a", envelope(type("SUV_XL", true)));
        fake.bodyByTenant.put("", envelope(type("BIKE", true)));

        MDC.put(TenantIdPropagation.MDC_KEY, "org-a");
        assertThat(catalogue.codes(null)).contains(java.util.Set.of("SUV_XL"));
        MDC.clear();
        assertThat(catalogue.codes(null)).contains(java.util.Set.of("BIKE"));
    }

    @Test
    void outageServesTheLastKnownSetThenAnswersNotKnown() {
        fake.bodyByTenant.put("org-a", envelope(type("BIKE", true)));
        assertThat(catalogue.codes("org-a")).isPresent();

        fake.down = true;
        clock.advance(Duration.ofMinutes(5)); // past pollInterval, within staleTtl
        assertThat(catalogue.codes("org-a")).contains(java.util.Set.of("BIKE"));

        clock.advance(Duration.ofMinutes(6)); // 11 minutes after the last fetch: past staleTtl
        assertThat(catalogue.codes("org-a")).isEmpty();

        fake.down = false;
        assertThat(catalogue.codes("org-a")).contains(java.util.Set.of("BIKE"));
    }

    @Test
    void outageWithNothingCachedIsNotKnownNeverAnException() {
        fake.down = true;
        assertThat(catalogue.codes("org-a")).isEmpty();
    }

    @Test
    void aBodyThatIsNotAListIsTreatedAsUnavailable() {
        fake.bodyByTenant.put("org-a", "{\"success\":true,\"responseObject\":{\"not\":\"a list\"}}");
        assertThat(catalogue.codes("org-a")).isEmpty();
        fake.bodyByTenant.put("org-b", "not json at all");
        assertThat(catalogue.codes("org-b")).isEmpty();
    }
}
