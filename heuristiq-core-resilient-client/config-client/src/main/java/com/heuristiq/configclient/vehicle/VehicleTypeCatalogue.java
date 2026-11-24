package com.heuristiq.configclient.vehicle;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heuristiq.configclient.CallerTenant;
import com.heuristiq.configclient.ConfigClientConstants;
import com.heuristiq.configclient.ConfigClientOptions;
import com.heuristiq.resilient.Idempotency;
import com.heuristiq.resilient.ResilientCaller;
import com.heuristiq.resilient.TenantIdPropagation;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Tells which vehicle-type codes exist for an organization, and how many passengers each seats, from the catalogue
 * Rider_ConfigService serves at {@code GET /internal/vehicle-types}. Only the codes and the passenger capacities are
 * kept: the endpoint also sends name, category and the active flag, which are ignored. The set is cached per organization
 * (one fetch serves both questions) and refreshed after {@code pollInterval};
 * during an outage the last known set is served for {@code staleTtl} after it was fetched, after which the answer is
 * empty ("not known"). A caller that needs a yes or no must make "not known" its own policy decision.
 *
 * <p>Methods never throw. A null or blank {@code tenantId} means the organization of the current request (MDC
 * {@code tenantId}), or the shared types only when there is none. Thread-safe.
 */
public final class VehicleTypeCatalogue {

    private static final Logger log = LoggerFactory.getLogger(VehicleTypeCatalogue.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** One organization's codes, the passengers each seats (only for entries that said so) and when they were fetched. */
    private record Snapshot(Set<String> codes, Map<String, Integer> capacities, Instant fetchedAt) { }

    /** What one fetch returned: the codes and the seats of those entries that carried a usable passenger capacity. */
    private record Fetched(Set<String> codes, Map<String, Integer> capacities) { }

    private final WebClient webClient;
    private final ResilientCaller caller;
    private final ConfigClientOptions options;
    private final Clock clock;

    private final Object cacheLock = new Object();
    private final Map<String, Snapshot> cache;
    private final Object fetchLock = new Object();
    private final AtomicBoolean outage = new AtomicBoolean(false);

    /**
     * @param webClient client whose base URL is the Config API root (e.g. {@code http://config:8080/api/v1/config})
     * @param caller    resilience wrapper for the Config downstream
     * @param options   service token, refresh interval ({@code pollInterval}) and outage window ({@code staleTtl})
     * @param clock     time source (injectable for tests)
     */
    public VehicleTypeCatalogue(WebClient webClient, ResilientCaller caller, ConfigClientOptions options, Clock clock) {
        this.webClient = webClient;
        this.caller = caller;
        this.options = options;
        this.clock = clock;
        this.cache = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Snapshot> eldest) {
                return size() > VehicleTypeConstants.MAX_CACHED_ORGANIZATIONS;
            }
        };
    }

    /**
     * The upper-case codes of every vehicle type visible to the organization (own and shared, switched on or off).
     *
     * @return the codes, or empty when the catalogue is not available (Config down and no recent copy)
     */
    public Optional<Set<String>> codes(String tenantId) {
        try {
            return load(CallerTenant.resolve(tenantId)).map(Snapshot::codes);
        } catch (RuntimeException e) {
            log.warn("Vehicle-type lookup failed ({}); answering not known", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /**
     * How many passengers each vehicle type seats, by upper-case code, for the organization (same cache, refresh and outage
     * rules as {@link #codes}). A type whose entry carried no usable capacity (not a whole number of at least 1) is left out of
     * the map, so "not in the map" means "capacity not known", not "seats nobody".
     *
     * @return the capacities, or empty when the catalogue is not available (Config down and no recent copy)
     */
    public Optional<Map<String, Integer>> capacities(String tenantId) {
        try {
            return load(CallerTenant.resolve(tenantId)).map(Snapshot::capacities);
        } catch (RuntimeException e) {
            log.warn("Vehicle-type capacity lookup failed ({}); answering not known", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /** Number of organizations cached (for tests and diagnostics). */
    int cachedOrganizations() {
        synchronized (cacheLock) {
            return cache.size();
        }
    }

    // ---- internals -----------------------------------------------------------------------------

    private Optional<Snapshot> load(String tenant) {
        String key = tenant == null ? VehicleTypeConstants.NO_TENANT_KEY : tenant;
        Snapshot cached = cacheGet(key);
        if (isFresh(cached)) {
            return Optional.of(cached);
        }
        synchronized (fetchLock) {
            cached = cacheGet(key);
            if (isFresh(cached)) {
                return Optional.of(cached);
            }
            try {
                Fetched fetched = fetch(tenant);
                Snapshot snapshot = new Snapshot(fetched.codes(), fetched.capacities(), clock.instant());
                synchronized (cacheLock) {
                    cache.put(key, snapshot);
                }
                if (outage.compareAndSet(true, false)) {
                    log.info("Config vehicle types reachable again");
                }
                return Optional.of(snapshot);
            } catch (RuntimeException e) {
                if (outage.compareAndSet(false, true)) {
                    log.warn("Config vehicle types unavailable ({}); serving the last known set while it is recent",
                            e.getClass().getSimpleName());
                }
                if (cached != null
                        && Duration.between(cached.fetchedAt(), clock.instant()).compareTo(options.staleTtl()) <= 0) {
                    return Optional.of(cached);
                }
                return Optional.empty();
            }
        }
    }

    private boolean isFresh(Snapshot s) {
        return s != null && Duration.between(s.fetchedAt(), clock.instant()).compareTo(options.pollInterval()) < 0;
    }

    private Snapshot cacheGet(String key) {
        synchronized (cacheLock) {
            return cache.get(key);
        }
    }

    private Fetched fetch(String tenant) {
        JsonNode body = caller.callBlocking(() -> {
            WebClient.RequestHeadersSpec<?> spec = webClient.get()
                    .uri(b -> b.path(VehicleTypeConstants.VEHICLE_TYPES_PATH).build())
                    .header(ConfigClientConstants.SERVICE_TOKEN_HEADER, options.serviceToken());
            if (tenant != null) {
                spec = spec.header(TenantIdPropagation.HEADER, tenant);
            }
            return spec.retrieve().bodyToMono(String.class).map(VehicleTypeCatalogue::parseJson);
        }, Idempotency.SAFE);
        if (body == null) {
            throw new IllegalStateException("empty response from config service");
        }
        JsonNode list = body.path(VehicleTypeConstants.FIELD_RESPONSE_OBJECT);
        if (!list.isArray()) {
            throw new IllegalStateException("vehicle-type response is not a list");
        }
        Set<String> codes = new LinkedHashSet<>();
        Map<String, Integer> capacities = new LinkedHashMap<>();
        for (JsonNode node : list) {
            String code = codeOf(node);
            if (code != null) {
                codes.add(code);
                Integer seats = capacityOf(node);
                if (seats != null) {
                    capacities.put(code, seats);
                }
            }
        }
        return new Fetched(Set.copyOf(codes), Map.copyOf(capacities));
    }

    /** The passenger capacity of one wire entry: a whole number of at least 1; null when absent, not a number or not usable. */
    static Integer capacityOf(JsonNode node) {
        JsonNode value = node.isObject() ? node.get(VehicleTypeConstants.FIELD_PASSENGER_CAPACITY) : null;
        return value == null || !value.isIntegralNumber() || value.asInt() < VehicleTypeConstants.MIN_CAPACITY
                ? null : value.asInt();
    }

    /** The code of one wire entry: the code field of an object, or the text itself (a plain list of codes); null otherwise. */
    static String codeOf(JsonNode node) {
        JsonNode value = node.isObject() ? node.get(VehicleTypeConstants.FIELD_CODE) : node;
        return value == null || !value.isTextual() || value.asText().isBlank()
                ? null : value.asText().trim().toUpperCase(Locale.ROOT);
    }

    private static JsonNode parseJson(String body) {
        try {
            return MAPPER.readTree(body);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
