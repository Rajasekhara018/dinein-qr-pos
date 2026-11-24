package com.heuristiq.configclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.heuristiq.resilient.ResilienceProfile;
import com.heuristiq.resilient.ResilientCaller;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

class ConfigClientTest {

    /** Mutable clock. */
    private static final class TestClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advance(Duration d) { now = now.plus(d); }

        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    /** Fake Config service: one handler per endpoint, records every request. */
    private static final class Fake {
        final List<ClientRequest> requests = new CopyOnWriteArrayList<>();
        Function<ClientRequest, String> resolve = r -> "";
        Function<ClientRequest, String> changes = r -> "";
        volatile boolean down;

        Mono<ClientResponse> handle(ClientRequest req) {
            requests.add(req);
            if (down) {
                return Mono.just(ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR).build());
            }
            String body = req.url().getPath().endsWith(ConfigClientConstants.RESOLVE_PATH)
                    ? resolve.apply(req) : changes.apply(req);
            return Mono.just(ClientResponse.create(HttpStatus.OK, ExchangeStrategies.withDefaults())
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE).body(body).build());
        }

        long count(String path) {
            return requests.stream().filter(r -> r.url().getPath().endsWith(path)).count();
        }
    }

    private final Fake fake = new Fake();
    private final TestClock clock = new TestClock();
    private ConfigClient client;

    @BeforeEach
    void setUp() {
        client = newClient(ConfigClientOptions.of("tok"));
    }

    private ConfigClient newClient(ConfigClientOptions options) {
        WebClient web = WebClient.builder().baseUrl("http://config/api/v1/config").exchangeFunction(fake::handle).build();
        ResilientCaller caller = new ResilientCaller("config-test", ResilienceProfile.defaults().withMaxAttempts(1));
        return new ConfigClient(web, caller, options, clock);
    }

    private static String resolved(String key, String value, String type) {
        return "{\"success\":true,\"responseObject\":{\"version\":1,\"values\":{\"" + key + "\":{\"value\":\""
                + value + "\",\"type\":\"" + type + "\",\"source\":\"GLOBAL\",\"version\":1}},\"unknownKeys\":[]}}";
    }

    private static String unknown(String key) {
        return "{\"success\":true,\"responseObject\":{\"version\":1,\"values\":{},\"unknownKeys\":[\"" + key + "\"]}}";
    }

    private static String changesJson(long current, boolean truncated, String... changes) {
        return "{\"success\":true,\"responseObject\":{\"currentVersion\":" + current + ",\"changes\":["
                + String.join(",", changes) + "],\"truncated\":" + truncated + "}}";
    }

    private static String change(long v, String key, String scopeType, String scopeId) {
        return "{\"version\":" + v + ",\"key\":\"" + key + "\",\"scopeType\":\"" + scopeType + "\",\"scopeId\":\""
                + scopeId + "\"}";
    }

    private void respondWith(String key, String value, String type) {
        fake.resolve = r -> resolved(key, value, type);
    }

    @Test
    void typedGetters() {
        respondWith("k.int", "45", "INT");
        assertThat(client.getInt("k.int", 1)).isEqualTo(45);
        respondWith("k.long", "9000000000", "INT");
        assertThat(client.getLong("k.long", 1L)).isEqualTo(9_000_000_000L);
        respondWith("k.dec", "33.335", "DECIMAL");
        assertThat(client.getDecimal("k.dec", BigDecimal.ONE)).isEqualByComparingTo(new BigDecimal("33.335"));
        assertThat(client.getDecimal("k.dec", BigDecimal.ONE).toPlainString()).isEqualTo("33.335");
        respondWith("k.bool", "true", "BOOLEAN");
        assertThat(client.getBoolean("k.bool", false)).isTrue();
        respondWith("k.dur", "90", "DURATION_SECONDS");
        assertThat(client.getDuration("k.dur", Duration.ZERO)).isEqualTo(Duration.ofSeconds(90));
        respondWith("k.str", "hello", "STRING");
        assertThat(client.getString("k.str", "x")).isEqualTo("hello");
    }

    @Test
    void cityResolveSendsCityAndKeepsEntriesPerCity() {
        fake.resolve = r -> r.url().getQuery().contains("city=blr") ? resolved("k", "10", "INT") : resolved("k", "20", "INT");
        assertThat(client.getInt("k", "blr", 1)).isEqualTo(10);
        assertThat(client.getInt("k", 1)).isEqualTo(20);
        assertThat(client.getInt("k", "BLR", 1)).isEqualTo(10);
        assertThat(fake.count(ConfigClientConstants.RESOLVE_PATH)).isEqualTo(2);
        assertThat(fake.requests.get(0).url().getQuery()).contains("keys=k").contains("city=blr");
        assertThat(fake.requests.get(1).url().getQuery()).doesNotContain("city");
    }

    @Test
    void cacheHitDoesNotCallAgain() {
        respondWith("k", "5", "INT");
        client.getInt("k", 1);
        client.getInt("k", 1);
        client.getInt("k", 1);
        assertThat(fake.count(ConfigClientConstants.RESOLVE_PATH)).isEqualTo(1);
    }

    @Test
    void fallbackWhenConfigDownAndNothingCached() {
        fake.down = true;
        assertThat(client.getInt("k", 7)).isEqualTo(7);
        assertThat(client.getDecimal("k2", new BigDecimal("1.5"))).isEqualTo(new BigDecimal("1.5"));
    }

    @Test
    void servesLastKnownValueWhenConfigGoesDownAfterHit() {
        respondWith("k", "5", "INT");
        assertThat(client.getInt("k", 1)).isEqualTo(5);
        fake.down = true;
        clock.advance(Duration.ofMinutes(11)); // past staleTtl: forces a re-resolve attempt
        assertThat(client.getInt("k", 1)).isEqualTo(5);
        assertThat(fake.count(ConfigClientConstants.RESOLVE_PATH)).isEqualTo(2);
    }

    @Test
    void staleEntryIsReresolvedWhenConfigIsBack() {
        respondWith("k", "5", "INT");
        client.getInt("k", 1);
        clock.advance(Duration.ofMinutes(11));
        respondWith("k", "6", "INT");
        assertThat(client.getInt("k", 1)).isEqualTo(6);
    }

    @Test
    void parseFailureReturnsFallback() {
        respondWith("k", "not-a-number", "INT");
        assertThat(client.getInt("k", 3)).isEqualTo(3);
        respondWith("b", "maybe", "BOOLEAN");
        assertThat(client.getBoolean("b", true)).isTrue();
    }

    @Test
    void unknownKeyReturnsFallbackAndIsNegativeCached() {
        fake.resolve = r -> unknown("nope");
        assertThat(client.getInt("nope", 9)).isEqualTo(9);
        assertThat(client.getInt("nope", 9)).isEqualTo(9);
        assertThat(fake.count(ConfigClientConstants.RESOLVE_PATH)).isEqualTo(1);
        clock.advance(ConfigClientConstants.NEGATIVE_TTL.plusSeconds(1));
        client.getInt("nope", 9);
        assertThat(fake.count(ConfigClientConstants.RESOLVE_PATH)).isEqualTo(2);
    }

    @Test
    void refreshFirstCallOnlyRecordsBaseline() {
        respondWith("k", "5", "INT");
        client.getInt("k", 1);
        fake.changes = r -> changesJson(10, false, change(10, "k", "GLOBAL", ""));
        client.refresh();
        assertThat(client.cacheSize()).isEqualTo(1);
    }

    @Test
    void refreshGlobalChangeDropsAllCitiesForKey() {
        respondWith("k", "5", "INT");
        client.getInt("k", 1);
        client.getInt("k", "BLR", 1);
        respondWith("other", "1", "INT");
        client.getInt("other", 1);
        fake.changes = r -> changesJson(10, false);
        client.refresh();
        fake.changes = r -> changesJson(11, false, change(11, "k", "GLOBAL", ""));
        client.refresh();
        assertThat(client.cacheSize()).isEqualTo(1);
        assertThat(fake.requests.get(fake.requests.size() - 1).url().getQuery()).isEqualTo("since=10");
    }

    @Test
    void refreshCityChangeDropsOnlyThatCity() {
        respondWith("k", "5", "INT");
        client.getInt("k", 1);
        client.getInt("k", "BLR", 1);
        client.getInt("k", "DEL", 1);
        fake.changes = r -> changesJson(10, false);
        client.refresh();
        fake.changes = r -> changesJson(11, false, change(11, "k", "CITY", "blr"));
        client.refresh();
        assertThat(client.cacheSize()).isEqualTo(2);
    }

    @Test
    void refreshTruncatedOrBackwardsVersionClearsEverything() {
        respondWith("k", "5", "INT");
        client.getInt("k", 1);
        fake.changes = r -> changesJson(10, false);
        client.refresh();
        fake.changes = r -> changesJson(20, true);
        client.refresh();
        assertThat(client.cacheSize()).isZero();

        client.getInt("k", 1);
        fake.changes = r -> changesJson(3, false); // went backwards (Config reset)
        client.refresh();
        assertThat(client.cacheSize()).isZero();
    }

    @Test
    void refreshFailureIsSwallowed() {
        fake.down = true;
        client.refresh();
        client.refresh();
        assertThat(client.cacheSize()).isZero();
    }

    @Test
    void cacheIsBounded() {
        ConfigClient small = newClient(new ConfigClientOptions("tok", Duration.ofSeconds(30), 5, Duration.ofMinutes(10)));
        fake.resolve = r -> resolved("x", "1", "INT");
        for (int i = 0; i < 25; i++) {
            small.getInt("key" + i, 0);
        }
        assertThat(small.cacheSize()).isEqualTo(5);
    }

    @Test
    void serviceTokenHeaderSentOnBothEndpoints() {
        respondWith("k", "5", "INT");
        client.getInt("k", 1);
        fake.changes = r -> changesJson(1, false);
        client.refresh();
        assertThat(fake.requests).hasSize(2);
        assertThat(fake.requests).allSatisfy(r ->
                assertThat(r.headers().getFirst(ConfigClientConstants.SERVICE_TOKEN_HEADER)).isEqualTo("tok"));
        assertThat(new ArrayList<>(fake.requests.stream().map(r -> r.url().getPath()).toList()))
                .containsExactly("/api/v1/config/internal/resolve", "/api/v1/config/internal/changes");
    }

    @Test
    void startAndCloseAreIdempotent() {
        assertThat(client.isStarted()).isFalse();
        client.start();
        client.start();
        assertThat(client.isStarted()).isTrue();
        client.close();
        client.close();
        assertThat(client.isStarted()).isFalse();
        client.start(); // no restart after close
        assertThat(client.isStarted()).isFalse();
    }

    @Test
    void optionsValidation() {
        assertThatThrownBy(() -> ConfigClientOptions.of(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ConfigClientOptions("t", Duration.ZERO, 10, Duration.ofMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ConfigClientOptions("t", Duration.ofSeconds(1), 0, Duration.ofMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(ConfigClientOptions.of("secret").toString()).doesNotContain("secret");
    }
}
