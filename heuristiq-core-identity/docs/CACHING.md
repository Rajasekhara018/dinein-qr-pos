# UAM read caching

In-process Caffeine caches (hand-built, same style as the gateway's `UserStatusCacheService`; no Spring Cache annotations, so no self-invocation traps and no annotations on domain code). Code: `com.heuristiq.uam.cache`. Each cache is bounded, expires after write, loads single-flight (`cache.get(key, loader)`), and publishes Micrometer meters (`cache.gets{result}`, `cache.size`, `cache.evictions`, `cache.puts`, `cache.load.duration`, tag `cache=<name>`).

## Caches

| Name (meter tag) | Seam | Key | Default TTL | Default max | Evicted by |
|---|---|---|---|---|---|
| `uam.my-privileges` | `CachingMyPrivilegesService` (primary decorator of `MyPrivilegesServiceUseCase`) | session tenant id (or `~none`) + user id | 30 s | 10 000 | user save/delete (that user); any role or permission save/delete (all) |
| `uam.my-organizations` | `CachingTenantMembershipService` (`organizationsOf` only) | session tenant id (or `~none`) + user id | 30 s | 10 000 | membership save (that user); user save/delete (that user); any tenant save/delete (all) |
| `uam.tenants` | `CachingTenantRepository` (`findById`, `findByCodeIgnoreCase`; code key lower-cased) | lookup kind + id/code | 120 s | 2 000 | any tenant save/delete (all) |
| `uam.tenant-applications` | `CachingTenantApplicationService` (`activeApplicationCodes` only) | tenant id | 120 s | 2 000 | tenant-application save (that tenant); any tenant save/delete (all) |

Eviction is wired once, on the repository ports (`UamCacheBeanPostProcessor`), so it covers every writer: services, `UamBootstrapRunner`, `DefaultOrganizationBootstrap`, membership backfill, org-structure/role-rule flows, registration, Google/OTP/Keycloak sync, import. It runs right after the call (also when it throws) and again after commit when a transaction is open.

Wired writers: `UamUserRepository` (save, delete), `UamRoleRepository` (save, delete), `PermissionRepository` (save, delete), `TenantMembershipRepository` (save), `TenantApplicationRepository` (save), `TenantRepository` (save, delete). Not wired (deliberately): security policy and the application registry (their data is not read by any cached path, and no policy lookup happens on a hot read path today); direct database edits made outside UAM (only the TTL applies).

## Staleness

Evictions are per instance. With several UAM instances, the other instances converge when their TTL ends (30 s for privileges and organizations, 120 s for organization lookups and application codes). The gateway also caches role to privileges for 30 s, so a role or permission change can take up to 30 s (UAM `/me/privileges` view) plus 30 s (gateway enforcement) = about 60 s to be fully visible across instances; on the instance that took the write the UAM view is immediate. A suspended organization can still open a session through another instance for up to 120 s.

## Deliberately not cached

Password hashes and credentials, login attempt and lockout state, refresh-token and session validity, OTP/MFA verification, `resolveForLogin` (access decision, only its tenant lookups are cached), membership status checks, user lookups, lists/searches/existence checks, "not found" results and any failure (a loader that throws or returns nothing stores nothing). Cached values are immutable records or `List.copyOf` snapshots; `Tenant` (mutable) is stored as a private copy and every caller gets a fresh copy.

## Operating

Properties (`railcab.uam.cache.*`, validated: TTL 1..600 s, size 1..1 000 000):

| Env var | Meaning |
|---|---|
| `UAM_CACHE_ENABLED` | global switch; `false` makes every path a pass-through (reads the database as before) |
| `UAM_CACHE_MY_PRIVILEGES_ENABLED` / `_TTL` / `_MAX_SIZE` | per cache; same pattern for `MY_ORGANIZATIONS`, `TENANTS`, `TENANT_APPLICATIONS` |

Watch `cache.gets` hit/miss ratio and `cache.size` against the max. To rule the cache out while debugging, set `UAM_CACHE_ENABLED=false` and restart.
