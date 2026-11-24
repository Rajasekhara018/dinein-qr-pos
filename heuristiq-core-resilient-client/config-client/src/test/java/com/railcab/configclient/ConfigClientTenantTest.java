package com.heuristiq.configclient;

import static org.assertj.core.api.Assertions.assertThat;

import com.heuristiq.resilient.ResilienceProfile;
import com.heuristiq.resilient.ResilientCaller;
import com.heuristiq.resilient.TenantIdPropagation;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
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

/** Organization separation of the config client: tenant in the cache key, X-Tenant-Id, change-feed invalidation. */
class ConfigClientTenantTest {

    private final List<ClientRequest> requests = new CopyOnWriteArrayList<>();
    /** Value answered per organization header ("" = none). */
    private Function<String, String> valueFor = t -> t.isEmpty() ? "1" : t.equals("org-a") ? "10" : "20";
    private String changesBody = changes(0);

    private final ConfigClient client = new ConfigClient(
            WebClient.builder().baseUrl("http://config/api/v1/config").exchangeFunction(this::handle).build(),
            new ResilientCaller("config-tenant-test", ResilienceProfile.defaults().withMaxAttempts(1)),
            ConfigClientOptions.of("tok"), Clock.fixed(Instant.parse("2026-11-04T00:00:00Z"), ZoneOffset.UTC));

    private Mono<ClientResponse> handle(ClientRequest req) {
        requests.add(req);
        String tenant = req.headers().getFirst(TenantIdPropagation.HEADER);
        String body;
        if (req.url().getPath().endsWith(ConfigClientConstants.RESOLVE_PATH)) {
            String key = req.url().getQuery().replaceAll(".*keys=([^&]+).*", "$1");
            body = "{\"success\":true,\"responseObject\":{\"version\":1,\"values\":{\"" + key + "\":{\"value\":\""
                    + valueFor.apply(tenant == null ? "" : tenant) + "\",\"type\":\"INT\",\"source\":\"GLOBAL\","
                    + "\"version\":1}},\"unknownKeys\":[]}}";
        } else {
            body = changesBody;
        }
        return Mono.just(ClientResponse.create(HttpStatus.OK, ExchangeStrategies.withDefaults())
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE).body(body).build());
    }

    private static String changes(long current, String... items) {
        return "{\"success\":true,\"responseObject\":{\"currentVersion\":" + current + ",\"changes\":["
                + String.join(",", items) + "],\"truncated\":false}}";
    }

    private static String change(long v, String key, String scopeType, String scopeId) {
        return "{\"version\":" + v + ",\"key\":\"" + key + "\",\"scopeType\":\"" + scopeType + "\",\"scopeId\":\""
                + scopeId + "\"}";
    }

    private long resolveCalls() {
        return requests.stream().filter(r -> r.url().getPath().endsWith(ConfigClientConstants.RESOLVE_PATH)).count();
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void anOrganizationsValueIsNeverServedToAnother() {
        assertThat(client.getInt("k", "BLR", "org-a", 0)).isEqualTo(10);
        assertThat(client.getInt("k", "BLR", "org-b", 0)).isEqualTo(20);
        assertThat(client.getInt("k", "BLR", null, 0)).isEqualTo(1);
        // cached separately: each organization keeps its own value, no further calls
        assertThat(client.getInt("k", "BLR", "org-a", 0)).isEqualTo(10);
        assertThat(client.getInt("k", "BLR", "org-b", 0)).isEqualTo(20);
        assertThat(resolveCalls()).isEqualTo(3);
        assertThat(client.cacheSize()).isEqualTo(3);
    }

    @Test
    void explicitOverloadsSendXTenantIdAndNoneWhenThereIsNoOrganization() {
        client.getInt("k", null, "org-a", 0);
        client.getInt("k2", 0);
        assertThat(requests.get(0).headers().getFirst(TenantIdPropagation.HEADER)).isEqualTo("org-a");
        assertThat(requests.get(1).headers().getFirst(TenantIdPropagation.HEADER)).isNull();
    }

    @Test
    void existingOverloadsUseTheRequestOrganizationFromMdc() {
        MDC.put(TenantIdPropagation.MDC_KEY, "org-a");
        assertThat(client.getInt("k", 0)).isEqualTo(10);
        assertThat(client.getInt("k", "blr", 0)).isEqualTo(10);
        assertThat(requests.get(0).headers().getFirst(TenantIdPropagation.HEADER)).isEqualTo("org-a");

        MDC.put(TenantIdPropagation.MDC_KEY, "org-b");
        assertThat(client.getInt("k", 0)).isEqualTo(20); // not org-a's cached value
        MDC.remove(TenantIdPropagation.MDC_KEY);
        assertThat(client.getInt("k", 0)).isEqualTo(1);
        // an explicit organization wins over the MDC one; blank means "the request's"
        MDC.put(TenantIdPropagation.MDC_KEY, "org-b");
        assertThat(client.getInt("k", null, "org-a", 0)).isEqualTo(10);
        assertThat(client.getInt("k", null, "  ", 0)).isEqualTo(20);
    }

    @Test
    void everyTypedOverloadWithAnOrganizationWorks() {
        valueFor = t -> "7";
        assertThat(client.getLong("a", "c", "org-a", 0L)).isEqualTo(7L);
        assertThat(client.getDecimal("b", "c", "org-a", BigDecimal.ZERO)).isEqualByComparingTo("7");
        assertThat(client.getDuration("d", "c", "org-a", Duration.ZERO)).isEqualTo(Duration.ofSeconds(7));
        assertThat(client.getString("e", "c", "org-a", "x")).isEqualTo("7");
        valueFor = t -> "true";
        assertThat(client.getBoolean("f", "c", "org-a", false)).isTrue();
        assertThat(requests).allSatisfy(r ->
                assertThat(r.headers().getFirst(TenantIdPropagation.HEADER)).isEqualTo("org-a"));
    }

    @Test
    void changeFeedInvalidatesTheKeyForEveryOrganization() {
        client.getInt("k", "BLR", "org-a", 0);
        client.getInt("k", "BLR", "org-b", 0);
        client.getInt("k", "DEL", "org-a", 0);
        client.getInt("k", null, "org-a", 0);
        client.getInt("other", "BLR", "org-a", 0);
        client.refresh(); // baseline
        changesBody = changes(2, change(2, "k", "CITY", "blr"));
        client.refresh();
        // BLR dropped for org-a and org-b; DEL, global and the other key stay
        assertThat(client.cacheSize()).isEqualTo(3);

        changesBody = changes(3, change(3, "k", "GLOBAL", ""));
        client.refresh();
        assertThat(client.cacheSize()).isEqualTo(1);

        valueFor = t -> "99";
        assertThat(client.getInt("k", "BLR", "org-b", 0)).isEqualTo(99);
    }

    @Test
    void changeFeedIsReadWithoutAnOrganization() {
        MDC.put(TenantIdPropagation.MDC_KEY, "org-a");
        client.refresh();
        assertThat(requests.get(0).url().getPath()).endsWith(ConfigClientConstants.CHANGES_PATH);
        assertThat(requests.get(0).headers().getFirst(TenantIdPropagation.HEADER)).isNull();
    }

    @Test
    void cacheKeyHasKeyCityAndOrganization() {
        assertThat(ConfigClient.cacheKey("k", " blr ", "org-a")).isEqualTo("k|BLR|org-a");
        assertThat(ConfigClient.cacheKey("k", null, null)).isEqualTo("k||");
    }

    @Test
    void callerTenantResolution() {
        assertThat(CallerTenant.resolve(" org-a ")).isEqualTo("org-a");
        assertThat(CallerTenant.resolve(null)).isNull();
        MDC.put(TenantIdPropagation.MDC_KEY, " org-m ");
        assertThat(CallerTenant.resolve("")).isEqualTo("org-m");
        MDC.put(TenantIdPropagation.MDC_KEY, " ");
        assertThat(CallerTenant.resolve(null)).isNull();
    }
}
