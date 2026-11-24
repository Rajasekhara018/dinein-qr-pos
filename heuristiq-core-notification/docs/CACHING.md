# NMS read caching

In-process Caffeine caches on the notification send path. Hand-built Caffeine (not Spring Cache) was chosen to mirror the
API gateway's `UserStatusCacheService`: per-entry expiry (a "found" and a "not found" answer need different TTLs), a
single-flight `get(key, loader)`, and the same Micrometer binder. Nothing else in NMS uses `@Cacheable`, and annotations
would have put caching on application classes.

Code: `infrastructure/cache` (`OptionalTtlCache`, `CaffeineTemplateLookupCache`, `CachingOrganizationProfileResolver`,
`NmsCacheConstants`), settings in `NmsProperties.Cache` (`nms.cache.*`, validated: TTL 1..86400 s, size 1..100000).

## Caches

| Cache (metric `cache` tag) | Key | TTL found / not found | Max | Evicted by |
|---|---|---|---|---|
| `nms.organization-profile` | tenant id | 300 s / 30 s | 500 | TTL only |
| `nms.template` | (`shared` or `tenant:<id>`, normalized event, channel) | 60 s / 15 s | 1000 | every template writer (below), TTL |

**Organization profile** (`CachingOrganizationProfileResolver` in front of `UamOrganizationClient`, via the new
`OrganizationProfileSource` port). A found profile is kept 300 s. "Organization not found" (UAM 404 or an unsuccessful body)
is kept only 30 s so a newly created organization shows up quickly. A failed lookup (UAM down, timeout, open circuit) is
never kept: it is logged and answered with an empty profile, so the message uses the platform name exactly as before, and the
next message tries UAM again. The profile has no writer in NMS (UAM owns it): an organization rename or a changed support
address is visible after at most the TTL (300 s) on each instance. There is no existing internal evict endpoint, so none was
added. This replaces the hand-rolled 10-minute `ConcurrentHashMap` that lived inside `UamOrganizationClient`.

**Resolved template** (`CaffeineTemplateLookupCache`, port `TemplateLookupCache`, used by `TemplateService.findForTenant`
and therefore `findByEventAndChannel`). The cached value is the final answer of the fallback (the organization's own active
template, else the shared one, else none), so org A's key is separate from org B's and from `shared`; the `tenant:` prefix
means an organization literally named `shared` cannot read the shared entry. "No template" is kept 15 s. Stored templates are
copies taken at load time and each caller receives its own copy, so mutating a returned template never changes the cache.

## Invalidation (template cache)

All writers go through `TemplateService`; each calls `evictAll()` in a `finally` after the repository write, so the edit is
visible at once on the same instance, even when the write fails part-way:

- wired: `create`, `update` (also covers activate/deactivate and event/channel changes), `delete` (soft delete),
  `seedDefaultsIfMissing` (startup seeding of default templates).
- skipped on purpose (no effect on the cached outcome): `findById`, `findByEvent`, `findAll`, the paged `findAll`, and the
  search reindex (reads only).
- There is no bulk or import path for templates in NMS.

The whole cache is cleared rather than one key because a shared template change affects every organization's fallback result
and an update may change the event or channel; template writes are rare. Other NMS instances do not hear about a write and
converge within the TTL (60 s; 15 s for a cached "no template"). A load that starts before a write and finishes after the
eviction can leave one stale entry until the TTL.

## Not cached

- `findById`, `findByEvent`, lists and search: admin screens, low frequency, must be exact.
- Rendered output, subject or body per message (depends on per-message variables).
- Any failure or exception, from UAM or the database.
- Organization data beyond name, code and support contacts.

## Metrics

Micrometer, tag `cache=<name>`: `cache.gets{result=hit|miss}`, `cache.size`, `cache.evictions`, `cache.puts`, and
`cache.load.duration` (timer around each loader call, failures included). Watch the hit ratio before tuning TTLs.

## Configuration and switches

| Property | Env | Default |
|---|---|---|
| `nms.cache.enabled` (master) | `NMS_CACHE_ENABLED` | true |
| `nms.cache.organization-profile.enabled` | `NMS_CACHE_ORGANIZATION_PROFILE_ENABLED` | true |
| `nms.cache.organization-profile.ttl-seconds` | `NMS_CACHE_ORGANIZATION_PROFILE_TTL_SECONDS` | 300 |
| `nms.cache.organization-profile.not-found-ttl-seconds` | `NMS_CACHE_ORGANIZATION_PROFILE_NOT_FOUND_TTL_SECONDS` | 30 |
| `nms.cache.organization-profile.max-size` | `NMS_CACHE_ORGANIZATION_PROFILE_MAX_SIZE` | 500 |
| `nms.cache.template.enabled` | `NMS_CACHE_TEMPLATE_ENABLED` | true |
| `nms.cache.template.ttl-seconds` | `NMS_CACHE_TEMPLATE_TTL_SECONDS` | 60 |
| `nms.cache.template.not-found-ttl-seconds` | `NMS_CACHE_TEMPLATE_NOT_FOUND_TTL_SECONDS` | 15 |
| `nms.cache.template.max-size` | `NMS_CACHE_TEMPLATE_MAX_SIZE` | 1000 |

Disable one cache or all with the switches above: every call then goes straight to the repository / UAM (no copy, no
retention). Note that disabling the profile cache also removes the old 10-minute in-client cache, so UAM is called per message.
