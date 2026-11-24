# Rider config client (`realboom-config-client`, inside `Rider_ResilientClient`)

Shared, Boot-agnostic library that lets Rider services read live settings from `Rider_ConfigService`
instead of hard-coding them. Typed getters (`getInt`, `getLong`, `getDecimal`, `getBoolean`,
`getDuration`, `getString`, each with an optional city argument) never throw: they use a bounded
LRU cache, resolve misses via `GET /internal/resolve`, serve the last known value if Config is down,
and otherwise return the fallback you pass. `start()` polls `GET /internal/changes` so edits show up
within one poll interval (default 30 s).

## Include it

This library is a module of the `Rider_ResilientClient` build (folder `Rider_ResilientClient/config-client`), so the
one include a service already has provides it:

`settings.gradle`:

```groovy
includeBuild('../Rider_ResilientClient')
```

`build.gradle`:

```groovy
implementation 'com.heuristiq:realboom-config-client'
```

The consumer needs spring-webflux, SLF4J and Jackson (any Spring Boot web/webflux service has them).
Resilient client comes in transitively (`api`).

**Dockerfile / CI:** like Rider_ResilientClient, nothing special is copied into the image. The
Dockerfiles do `COPY build/libs/*.jar app.jar`, so the service jar (with this library bundled from the
composite build) must be built on the host (`./gradlew bootJar`) before `docker build`, with
`Rider_ResilientClient` checked out as a sibling of the service folder (nothing else is needed: the config client
is part of that folder, so no extra CI checkout).

## Example

```java
@Bean(destroyMethod = "close")
ConfigClient configClient(WebClient.Builder builder, @Value("${rider.config.url}") String url,
                          @Value("${rider.config.service-token}") String token) {
    WebClient web = builder.baseUrl(url)                       // e.g. http://config:8080/api/v1/config
            .filter(CorrelationIdPropagation.filter()).build();
    ResilientCaller caller = new ResilientCaller("config", ResilienceProfile.defaults());
    ConfigClient client = new ConfigClient(web, caller, ConfigClientOptions.of(token), Clock.systemUTC());
    client.start();
    return client;
}

// usage: the second argument is the built-in default, used when Config is down or the key is unknown
BigDecimal arrivingFee = configClient.getDecimal("ride.cancellation.driverArrivingFee", new BigDecimal("50.0"));
int batchSize = configClient.getInt("dispatch.batchSize", city, 3);
```

## Rule

**The fallback you pass MUST equal the catalog `defaultValue` in
`Rider_ConfigService/src/main/resources/config-definitions.json`.** That keeps "Config is down" or "key
not defined yet" identical to the old hard-coded behaviour. Never pass a money value as `double`; use
`getDecimal`.

## Behaviour notes

- Cache bounded by `maxCacheEntries` (default 2000, LRU). Unknown keys are negative-cached for 1 minute.
- Entries are trusted while the client has had successful contact within `staleTtl` (default 10 min);
  afterwards a read re-resolves, but a stale value is still served if Config is unreachable.
- Outages log one WARN and one INFO on recovery; a malformed value logs a WARN naming only the key.
- The service token is sent as `X-Rider-Service-Token`; the correlation id is forwarded by the
  `CorrelationIdPropagation` filter you add to the WebClient.

## Organizations

Every read is for an organization: `getX(key, fallback)` and `getX(key, city, fallback)` use the organization of the
current request (MDC key `tenantId`, `TenantIdPropagation.MDC_KEY`); `getX(key, city, tenantId, fallback)` names it
explicitly (use this on background threads; null or blank = the current request's). It is sent as `X-Tenant-Id`
and is part of the cache key (`key|CITY|tenant`), so one organization's value is never served to another. Without
an organization only shared values are read. A change in the feed drops the key for every organization.

## Which city is a point in (`com.heuristiq.configclient.city`)

```java
@Bean
CityLocator cityLocator(WebClient configWebClient, ResilientCaller configCaller, ConfigClientOptions options) {
    return new CityLocator(configWebClient, configCaller, options, Clock.systemUTC()); // same WebClient base URL as ConfigClient
}

CityLookup lookup = cityLocator.locate(pickupLat, pickupLng, tenantId);
switch (lookup.status()) {
    case FOUND -> lookup.area().code();      // city code for getX(key, city, tenantId, fallback); check area().isLive()
    case OUTSIDE -> { }                      // in no city of the organization
    case UNKNOWN -> { }                      // Config unreachable, nothing cached: never refuse a booking on this
}
```

`CityLocator` reads `GET /internal/cities/service-areas` once per organization and answers locally (polygon first,
else the circle around the centre). The list is refreshed after `pollInterval`; during an outage the last list is
served for `staleTtl` after it was fetched, then lookups answer `UNKNOWN`. An organization's own city wins over a
shared one, then a LIVE city over a paused one. Also `byCode(code, tenantId)` and `areas(tenantId)`; nothing throws.
`GeoMath` (haversine, point in polygon, distance to a polygon, circle as polygon, polygon overlap), `GeoPoint` and
`ServiceArea` (`contains`, `distanceOutsideKm`, `isLive`) are plain helpers usable on their own.

## Which vehicle-type codes exist (`com.heuristiq.configclient.vehicle`)

```java
@Bean
VehicleTypeCatalogue vehicleTypeCatalogue(WebClient configWebClient, ResilientCaller configCaller, ConfigClientOptions options) {
    return new VehicleTypeCatalogue(configWebClient, configCaller, options, Clock.systemUTC());
}

Optional<Set<String>> codes = vehicleTypeCatalogue.codes(tenantId);   // empty = "not known" (Config down, nothing recent cached)
```

`VehicleTypeCatalogue` reads `GET /internal/vehicle-types` once per organization and keeps only the upper-case `code`
of each entry (the endpoint also sends name, category, capacity and the active flag; they are ignored, and a plain list
of codes is read too). Same cache rules as `CityLocator`: refreshed after `pollInterval`, last set served for `staleTtl`
during an outage, then the answer is empty. It never throws. An empty answer is not "no such type": the caller decides
what "not known" means (Pricing accepts the code and logs a warning).
