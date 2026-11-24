package com.heuristiq.configclient;

import static com.heuristiq.configclient.ConfigClientConstants.CACHE_KEY_SEPARATOR;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heuristiq.resilient.Idempotency;
import com.heuristiq.resilient.ResilientCaller;
import com.heuristiq.resilient.TenantIdPropagation;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Reads live settings from Rider_ConfigService with a bounded local cache, so services stop
 * hard-coding values that operators can change.
 *
 * <p>Getters never throw. On a cache miss the key is resolved from Config; if Config is unreachable
 * the last known value is returned, else the caller's fallback. The fallback must equal the catalog
 * default, which makes "Config is down" behave exactly like today's hard-coded value. A value that
 * does not parse as the requested type also yields the fallback (a warning names the key, never the
 * value). Call {@link #start()} to poll the changes feed and drop changed keys promptly; thread-safe.
 *
 * <p>Values are read per organization (see {@link CallerTenant}): the organization is sent as {@code X-Tenant-Id}
 * and is part of the cache key ({@code key|city|tenant}), so one organization's value never reaches another.
 */
public final class ConfigClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ConfigClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Cached resolution; {@code value == null} marks a negative (unknown key) entry. */
    private record Entry(String value, Instant cachedAt) { }

    private final WebClient webClient;
    private final ResilientCaller caller;
    private final ConfigClientOptions options;
    private final Clock clock;

    private final Object cacheLock = new Object();
    private final Map<String, Entry> cache;

    private final Object refreshLock = new Object();
    private long lastVersion = ConfigClientConstants.NO_VERSION;

    private final AtomicBoolean outage = new AtomicBoolean(false);
    private volatile Instant lastContact;

    private ScheduledExecutorService poller;
    private boolean closed;

    /**
     * Creates a client; no thread is started until {@link #start()}.
     *
     * @param webClient client whose base URL is the Config API root (e.g. {@code http://config:8080/api/v1/config})
     * @param caller    resilience wrapper for the Config downstream
     * @param options   token, poll interval and cache bounds
     * @param clock     time source (injectable for tests)
     */
    public ConfigClient(WebClient webClient, ResilientCaller caller, ConfigClientOptions options, Clock clock) {
        this.webClient = webClient;
        this.caller = caller;
        this.options = options;
        this.clock = clock;
        this.lastContact = clock.instant();
        final int max = options.maxCacheEntries();
        this.cache = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Entry> eldest) {
                return size() > max;
            }
        };
    }

    // ---- typed getters -------------------------------------------------------------------------
    //
    // Every getter reads for an organization: the overloads without a tenantId use the organization of the current
    // request (MDC key TenantIdPropagation.MDC_KEY), the explicit ones use the one given (null or blank = the current
    // request's). The organization is sent as X-Tenant-Id and is part of the cache key, so one organization's value
    // is never served to another. Without any organization only the shared values are read.

    /** Integer setting, or {@code fallback} when unavailable or malformed. */
    public int getInt(String key, int fallback) { return getInt(key, null, null, fallback); }

    /** City-scoped integer setting (Config resolves city value, then global, then default). */
    public int getInt(String key, String city, int fallback) { return getInt(key, city, null, fallback); }

    /** City-scoped integer setting for an explicit organization. */
    public int getInt(String key, String city, String tenantId, int fallback) {
        return typed(key, city, tenantId, fallback, Integer::parseInt);
    }

    /** Long setting, or {@code fallback}. */
    public long getLong(String key, long fallback) { return getLong(key, null, null, fallback); }

    /** City-scoped long setting. */
    public long getLong(String key, String city, long fallback) { return getLong(key, city, null, fallback); }

    /** City-scoped long setting for an explicit organization. */
    public long getLong(String key, String city, String tenantId, long fallback) {
        return typed(key, city, tenantId, fallback, Long::parseLong);
    }

    /** Exact decimal (money, rates); parsed from the wire string, never via double. */
    public BigDecimal getDecimal(String key, BigDecimal fallback) { return getDecimal(key, null, null, fallback); }

    /** City-scoped exact decimal. */
    public BigDecimal getDecimal(String key, String city, BigDecimal fallback) {
        return getDecimal(key, city, null, fallback);
    }

    /** City-scoped exact decimal for an explicit organization. */
    public BigDecimal getDecimal(String key, String city, String tenantId, BigDecimal fallback) {
        return typed(key, city, tenantId, fallback, BigDecimal::new);
    }

    /** Boolean setting; only {@code true}/{@code false} (any case) are valid. */
    public boolean getBoolean(String key, boolean fallback) { return getBoolean(key, null, null, fallback); }

    /** City-scoped boolean setting. */
    public boolean getBoolean(String key, String city, boolean fallback) {
        return getBoolean(key, city, null, fallback);
    }

    /** City-scoped boolean setting for an explicit organization. */
    public boolean getBoolean(String key, String city, String tenantId, boolean fallback) {
        return typed(key, city, tenantId, fallback, ConfigClient::parseBoolean);
    }

    /** Duration setting (catalog type DURATION_SECONDS: whole seconds on the wire). */
    public Duration getDuration(String key, Duration fallback) { return getDuration(key, null, null, fallback); }

    /** City-scoped duration setting. */
    public Duration getDuration(String key, String city, Duration fallback) {
        return getDuration(key, city, null, fallback);
    }

    /** City-scoped duration setting for an explicit organization. */
    public Duration getDuration(String key, String city, String tenantId, Duration fallback) {
        return typed(key, city, tenantId, fallback, s -> Duration.ofSeconds(Long.parseLong(s)));
    }

    /** String (or ENUM) setting. */
    public String getString(String key, String fallback) { return getString(key, null, null, fallback); }

    /** City-scoped string setting. */
    public String getString(String key, String city, String fallback) { return getString(key, city, null, fallback); }

    /** City-scoped string setting for an explicit organization. */
    public String getString(String key, String city, String tenantId, String fallback) {
        return typed(key, city, tenantId, fallback, s -> s);
    }

    // ---- refresh / lifecycle -------------------------------------------------------------------

    /**
     * Polls the changes feed and drops cached entries for changed keys. The first call only records
     * the baseline version; a truncated feed or a version that went backwards clears the whole cache.
     * Failures are swallowed (logged once per outage).
     */
    public void refresh() {
        synchronized (refreshLock) {
            try {
                long since = lastVersion;
                JsonNode body = fetch(ConfigClientConstants.CHANGES_PATH, since < 0 ? 0L : since, null, null, null);
                markContact();
                JsonNode data = body.path("responseObject");
                long current = data.path("currentVersion").asLong(ConfigClientConstants.NO_VERSION);
                if (since >= 0) {
                    if (data.path("truncated").asBoolean(false) || current < since) {
                        clearCache();
                    } else {
                        for (JsonNode change : data.path("changes")) {
                            invalidate(change.path("key").asText(), change.path("scopeType").asText(),
                                    change.path("scopeId").asText(""));
                        }
                    }
                }
                lastVersion = current;
            } catch (RuntimeException e) {
                markFailure(e);
            }
        }
    }

    /** Starts the background poller (daemon thread). Idempotent; no-op after {@link #close()}. */
    public synchronized void start() {
        if (poller != null || closed) {
            return;
        }
        poller = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, ConfigClientConstants.POLLER_THREAD_NAME);
            t.setDaemon(true);
            return t;
        });
        long millis = options.pollInterval().toMillis();
        poller.scheduleWithFixedDelay(this::refresh, millis, millis, TimeUnit.MILLISECONDS);
    }

    /** Stops the poller. Idempotent; getters keep working from cache/fallback afterwards. */
    @Override
    public synchronized void close() {
        closed = true;
        if (poller != null) {
            poller.shutdownNow();
            poller = null;
        }
    }

    // ---- internals -----------------------------------------------------------------------------

    /** Number of cached entries (for tests and diagnostics). */
    int cacheSize() {
        synchronized (cacheLock) {
            return cache.size();
        }
    }

    synchronized boolean isStarted() {
        return poller != null;
    }

    private <T> T typed(String key, String city, String tenantId, T fallback, Parser<T> parser) {
        Optional<String> raw = lookup(key, city, CallerTenant.resolve(tenantId));
        if (raw.isEmpty()) {
            return fallback;
        }
        try {
            return parser.parse(raw.get());
        } catch (RuntimeException e) {
            log.warn("Config key '{}' has a value that is not valid for the requested type; using fallback", key);
            return fallback;
        }
    }

    private Optional<String> lookup(String key, String city, String tenant) {
        String ck = cacheKey(key, city, tenant);
        Entry cached = cacheGet(ck);
        Instant now = clock.instant();
        if (cached != null) {
            if (cached.value() == null) {
                if (Duration.between(cached.cachedAt(), now).compareTo(ConfigClientConstants.NEGATIVE_TTL) < 0) {
                    return Optional.empty();
                }
            } else if (Duration.between(lastContact, now).compareTo(options.staleTtl()) <= 0) {
                return Optional.of(cached.value());
            }
        }
        try {
            Optional<String> resolved = resolve(key, city, tenant);
            synchronized (cacheLock) {
                cache.put(ck, new Entry(resolved.orElse(null), clock.instant()));
            }
            return resolved;
        } catch (RuntimeException e) {
            markFailure(e);
            return cached == null || cached.value() == null ? Optional.empty() : Optional.of(cached.value());
        }
    }

    private Optional<String> resolve(String key, String city, String tenant) {
        JsonNode body = fetch(ConfigClientConstants.RESOLVE_PATH, null, key, city, tenant);
        markContact();
        JsonNode value = body.path("responseObject").path("values").path(key).path("value");
        return value.isMissingNode() || value.isNull() ? Optional.empty() : Optional.of(value.asText());
    }

    /** GET on the Config internal API; {@code tenant} (may be null) is sent as X-Tenant-Id. */
    private JsonNode fetch(String path, Long since, String key, String city, String tenant) {
        JsonNode node = caller.callBlocking(() -> {
            WebClient.RequestHeadersSpec<?> spec = webClient.get()
                    .uri(b -> {
                        b.path(path);
                        if (since != null) {
                            b.queryParam(ConfigClientConstants.PARAM_SINCE, since);
                        }
                        if (key != null) {
                            b.queryParam(ConfigClientConstants.PARAM_KEYS, key);
                            if (city != null && !city.isBlank()) {
                                b.queryParam(ConfigClientConstants.PARAM_CITY, city);
                            }
                        }
                        return b.build();
                    })
                    .header(ConfigClientConstants.SERVICE_TOKEN_HEADER, options.serviceToken());
            if (tenant != null) {
                spec = spec.header(TenantIdPropagation.HEADER, tenant);
            }
            return spec.retrieve().bodyToMono(String.class).map(ConfigClient::parseJson);
        }, Idempotency.SAFE);
        if (node == null) {
            throw new IllegalStateException("empty response from config service");
        }
        return node;
    }

    private static JsonNode parseJson(String body) {
        try {
            return MAPPER.readTree(body);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void markContact() {
        lastContact = clock.instant();
        if (outage.compareAndSet(true, false)) {
            log.info("Config service reachable again");
        }
    }

    private void markFailure(RuntimeException e) {
        if (outage.compareAndSet(false, true)) {
            log.warn("Config service unavailable ({}); serving cached values or fallbacks until it recovers",
                    e.getClass().getSimpleName());
        }
    }

    private Entry cacheGet(String ck) {
        synchronized (cacheLock) {
            return cache.get(ck);
        }
    }

    private void clearCache() {
        synchronized (cacheLock) {
            cache.clear();
        }
    }

    /**
     * Drops the cached entries a change affects, for every organization: the feed does not say whose value changed,
     * and an organization's effective value may come from the shared row. A CITY change drops that key for that
     * city; any other change drops the key for every city.
     */
    private void invalidate(String key, String scopeType, String scopeId) {
        String prefix = ConfigClientConstants.SCOPE_CITY.equalsIgnoreCase(scopeType)
                ? key + CACHE_KEY_SEPARATOR + normalizeCity(scopeId) + CACHE_KEY_SEPARATOR
                : key + CACHE_KEY_SEPARATOR;
        synchronized (cacheLock) {
            cache.keySet().removeIf(k -> k.startsWith(prefix));
        }
    }

    /** {@code key|CITY|tenant}: city upper-cased, empty parts when absent. */
    static String cacheKey(String key, String city, String tenant) {
        return key + CACHE_KEY_SEPARATOR + normalizeCity(city) + CACHE_KEY_SEPARATOR + (tenant == null ? "" : tenant);
    }

    private static String normalizeCity(String city) {
        return city == null ? "" : city.trim().toUpperCase(Locale.ROOT);
    }

    private static boolean parseBoolean(String s) {
        if ("true".equalsIgnoreCase(s)) {
            return true;
        }
        if ("false".equalsIgnoreCase(s)) {
            return false;
        }
        throw new IllegalArgumentException("not a boolean");
    }

    @FunctionalInterface
    private interface Parser<T> {
        T parse(String raw);
    }
}
