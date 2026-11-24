# DMS read caching

In-process Caffeine caches (hand-built, as in the gateway's `UserStatusCacheService`; no Spring Cache annotations, so the
domain and use cases stay free of caching). Settings: `railcab.dms.cache.*` (`DmsCacheProperties`, validated), names and
defaults in `DmsCacheConstants`.

## Caches

### `dms.document-type-rule`

| | |
|---|---|
| What | the effective `DocumentTypeRule` (formats, extensions, content types, size, retention, scanRequired) of an active type |
| Used by | every upload naming a document type (`DocumentValidationService` -> `DocumentTypeRules.resolveRule`) and the file-check endpoint |
| Key | `(tenantId, trimmed upper-case code)`: the same code in two organizations is two independent entries |
| TTL / max | 60 s (`ttl-seconds`, 1..86400) / 5000 entries (`max-size`), `expireAfterWrite` |
| Loading | single-flight (`Cache.get(key, loader)`): a burst of misses causes one database read |
| Where | `CachingDocumentTypeRules` (decorator, `documenttype/adapter/out/cache`), `@Primary` bean in `DocumentTypeCacheConfig` over `DocumentTypeQueryService` |
| Evictors | `DocumentTypeApplyService.apply`, right after `repository.save` succeeds (the only writer of document types; create, update, deactivation, formats, size, retention, scanRequired all go through it). Evicts exactly `(tenant, code)` |
| Other instances | converge within the TTL (60 s by default); only the instance that applied the change evicts at once |

Behaviour kept identical:
- the category check runs on every call against the cached rule's category (a wrong category is still INVALID with the same message);
- failures (unknown or inactive type, blank input) are never cached and there is no negative cache, so a type approved a moment ago is never hidden; a deactivated type disappears at once on the applying instance and within the TTL elsewhere;
- blocked extensions/content types are read from `railcab.dms.scan.*` at load time; a change of those properties is picked up after the TTL.

Metrics (Micrometer, tag `cache=dms.document-type-rule`): `cache.gets{result=hit|miss}`, `cache.size`, `cache.evictions`,
`cache.puts`, and `cache.load.duration` (timer around the database load).

## Deliberately not cached

- Lifecycle current-state search and detail endpoints: used for troubleshooting, must be fresh.
- The DMS-local audit and the audit outbox: write paths, and they must reflect every event.
- Document metadata, download tokens and scan results: change per request/upload and carry authorization decisions.
- Document-type lists and get-by-id: admin screens, low volume, must show the latest state.

## Switching off

- `DMS_CACHE_ENABLED=false`: every cache is a pass-through (each lookup reads the database, as before the cache existed).
- `DMS_CACHE_DOCUMENT_TYPE_RULE_ENABLED=false`: only this cache.
- `DMS_CACHE_DOCUMENT_TYPE_RULE_TTL_SECONDS`, `DMS_CACHE_DOCUMENT_TYPE_RULE_MAX_SIZE`: tuning.
