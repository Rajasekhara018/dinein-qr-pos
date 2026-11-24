package com.heuristiq.configclient.city;

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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Tells which operating city a map point is in, locally, from the organization's service-area list that
 * Rider_ConfigService serves at {@code GET /internal/cities/service-areas}. No remote call per lookup: the list is
 * cached per organization and refreshed after {@code pollInterval}; during an outage the last known list is served
 * for {@code staleTtl} after it was fetched, after which lookups answer {@link CityLookup.Status#UNKNOWN}.
 *
 * <p>When an organization's own city and a shared city both contain the point, the organization's own city wins;
 * then a LIVE city wins over a paused or coming-soon one. Methods never throw. A null or blank {@code tenantId}
 * means the organization of the current request (MDC {@code tenantId}), or shared cities only when there is none.
 * Thread-safe.
 */
public final class CityLocator {

    private static final Logger log = LoggerFactory.getLogger(CityLocator.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** One organization's list and when it was fetched. */
    private record Snapshot(List<ServiceArea> areas, Instant fetchedAt) { }

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
    public CityLocator(WebClient webClient, ResilientCaller caller, ConfigClientOptions options, Clock clock) {
        this.webClient = webClient;
        this.caller = caller;
        this.options = options;
        this.clock = clock;
        this.cache = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Snapshot> eldest) {
                return size() > CityConstants.MAX_CACHED_ORGANIZATIONS;
            }
        };
    }

    /**
     * The city containing the point.
     *
     * @return FOUND with the city; OUTSIDE when the point is in none; UNKNOWN when the list is not available (or the
     *         coordinates are not finite numbers)
     */
    public CityLookup locate(double lat, double lng, String tenantId) {
        try {
            if (!Double.isFinite(lat) || !Double.isFinite(lng)) {
                return CityLookup.unknown();
            }
            String tenant = CallerTenant.resolve(tenantId);
            Optional<List<ServiceArea>> areas = load(tenant);
            if (areas.isEmpty()) {
                return CityLookup.unknown();
            }
            return areas.get().stream()
                    .filter(a -> a.contains(lat, lng))
                    .min(preference(tenant))
                    .map(CityLookup::found)
                    .orElseGet(CityLookup::outside);
        } catch (RuntimeException e) {
            log.warn("City lookup failed ({}); answering UNKNOWN", e.getClass().getSimpleName());
            return CityLookup.unknown();
        }
    }

    /** The city with this code (case-insensitive) visible to the organization; its own city wins over a shared one. */
    public Optional<ServiceArea> byCode(String code, String tenantId) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        try {
            String tenant = CallerTenant.resolve(tenantId);
            String wanted = code.trim().toUpperCase(Locale.ROOT);
            return load(tenant).orElse(List.of()).stream()
                    .filter(a -> wanted.equalsIgnoreCase(a.code()))
                    .min(preference(tenant));
        } catch (RuntimeException e) {
            log.warn("City lookup by code failed ({})", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /** Every service area visible to the organization (own and shared); empty when not available. */
    public List<ServiceArea> areas(String tenantId) {
        try {
            return load(CallerTenant.resolve(tenantId)).orElse(List.of());
        } catch (RuntimeException e) {
            log.warn("City list failed ({})", e.getClass().getSimpleName());
            return List.of();
        }
    }

    /** Number of organizations cached (for tests and diagnostics). */
    int cachedOrganizations() {
        synchronized (cacheLock) {
            return cache.size();
        }
    }

    // ---- internals -----------------------------------------------------------------------------

    private static Comparator<ServiceArea> preference(String tenant) {
        return Comparator.<ServiceArea>comparingInt(a -> a.isOwnedBy(tenant) ? 0 : 1)
                .thenComparingInt(a -> a.isLive() ? 0 : 1);
    }

    private Optional<List<ServiceArea>> load(String tenant) {
        String key = tenant == null ? CityConstants.NO_TENANT_KEY : tenant;
        Snapshot cached = cacheGet(key);
        if (isFresh(cached)) {
            return Optional.of(cached.areas());
        }
        synchronized (fetchLock) {
            cached = cacheGet(key);
            if (isFresh(cached)) {
                return Optional.of(cached.areas());
            }
            try {
                List<ServiceArea> fetched = fetch(tenant);
                synchronized (cacheLock) {
                    cache.put(key, new Snapshot(fetched, clock.instant()));
                }
                if (outage.compareAndSet(true, false)) {
                    log.info("Config service areas reachable again");
                }
                return Optional.of(fetched);
            } catch (RuntimeException e) {
                if (outage.compareAndSet(false, true)) {
                    log.warn("Config service areas unavailable ({}); serving the last known list while it is recent",
                            e.getClass().getSimpleName());
                }
                if (cached != null
                        && Duration.between(cached.fetchedAt(), clock.instant()).compareTo(options.staleTtl()) <= 0) {
                    return Optional.of(cached.areas());
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

    private List<ServiceArea> fetch(String tenant) {
        JsonNode body = caller.callBlocking(() -> {
            WebClient.RequestHeadersSpec<?> spec = webClient.get()
                    .uri(b -> b.path(CityConstants.SERVICE_AREAS_PATH).build())
                    .header(ConfigClientConstants.SERVICE_TOKEN_HEADER, options.serviceToken());
            if (tenant != null) {
                spec = spec.header(TenantIdPropagation.HEADER, tenant);
            }
            return spec.retrieve().bodyToMono(String.class).map(CityLocator::parseJson);
        }, Idempotency.SAFE);
        if (body == null) {
            throw new IllegalStateException("empty response from config service");
        }
        JsonNode list = body.path(CityConstants.FIELD_RESPONSE_OBJECT);
        if (!list.isArray()) {
            throw new IllegalStateException("service-area response is not a list");
        }
        List<ServiceArea> result = new ArrayList<>();
        for (JsonNode node : list) {
            ServiceArea area = toArea(node);
            if (area != null) {
                result.add(area);
            }
        }
        return List.copyOf(result);
    }

    /** Maps one wire entry; an entry without a code or without any usable geometry is skipped. */
    static ServiceArea toArea(JsonNode n) {
        String code = text(n, CityConstants.FIELD_CODE);
        if (code == null) {
            return null;
        }
        GeoPoint center = null;
        if (n.path(CityConstants.FIELD_CENTER_LAT).isNumber() && n.path(CityConstants.FIELD_CENTER_LNG).isNumber()) {
            GeoPoint c = new GeoPoint(n.get(CityConstants.FIELD_CENTER_LAT).asDouble(),
                    n.get(CityConstants.FIELD_CENTER_LNG).asDouble());
            center = c.isValid() ? c : null;
        }
        Double radius = n.path(CityConstants.FIELD_SERVICE_RADIUS_KM).isNumber()
                ? n.get(CityConstants.FIELD_SERVICE_RADIUS_KM).asDouble() : null;
        List<GeoPoint> boundary = new ArrayList<>();
        for (JsonNode point : n.path(CityConstants.FIELD_BOUNDARY)) {
            if (point.isArray() && point.size() >= 2 && point.get(0).isNumber() && point.get(1).isNumber()) {
                GeoPoint p = new GeoPoint(point.get(0).asDouble(), point.get(1).asDouble());
                if (p.isValid()) {
                    boundary.add(p);
                }
            }
        }
        ServiceArea area = new ServiceArea(code.toUpperCase(Locale.ROOT), text(n, CityConstants.FIELD_NAME),
                text(n, CityConstants.FIELD_TIER), text(n, CityConstants.FIELD_TIMEZONE),
                text(n, CityConstants.FIELD_SERVICE_STATUS), center, radius, boundary,
                text(n, CityConstants.FIELD_TENANT_ID), text(n, CityConstants.FIELD_LOCATION_ID));
        return area.hasBoundary() || area.hasCircle() ? area : null;
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() || v.asText().isBlank() ? null : v.asText().trim();
    }

    private static JsonNode parseJson(String body) {
        try {
            return MAPPER.readTree(body);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
