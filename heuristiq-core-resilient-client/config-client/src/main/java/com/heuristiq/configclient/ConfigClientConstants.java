package com.heuristiq.configclient;

import java.time.Duration;

/** Fixed values of the Rider_ConfigService internal API and the client's defaults. */
public final class ConfigClientConstants {

    /** Header carrying the shared service token. */
    public static final String SERVICE_TOKEN_HEADER = "X-Rider-Service-Token";
    public static final String RESOLVE_PATH = "/internal/resolve";
    public static final String CHANGES_PATH = "/internal/changes";
    public static final String PARAM_CITY = "city";
    public static final String PARAM_KEYS = "keys";
    public static final String PARAM_SINCE = "since";

    public static final String SCOPE_CITY = "CITY";

    public static final Duration DEFAULT_POLL_INTERVAL = Duration.ofSeconds(30);
    public static final int DEFAULT_MAX_CACHE_ENTRIES = 2000;
    public static final Duration DEFAULT_STALE_TTL = Duration.ofMinutes(10);
    /** How long an unknown key is remembered so it does not hit Config on every call. */
    public static final Duration NEGATIVE_TTL = Duration.ofMinutes(1);

    /** Separator between key and city in cache keys; config keys never contain it. */
    public static final char CACHE_KEY_SEPARATOR = '|';
    public static final String POLLER_THREAD_NAME = "config-client-poller";
    public static final long NO_VERSION = -1L;

    private ConfigClientConstants() { }
}
