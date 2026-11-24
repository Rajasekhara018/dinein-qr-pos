# Rider_UAM index and query review for 10 million users (PostgreSQL)

Scope: the queries on the login, refresh, authorization-read and staff-screen paths, as written in the repository,
against the schema the Flyway migrations V1-V15 create. **This is a code review, not a measurement**: no query was
run against a 10-million-row database. What was run: the V15 migration and the index plans on a real (embedded)
PostgreSQL with a tiny table and `enable_seqscan = off` (proves an index is *usable* for the predicate, not that the
planner prefers it at scale), and the seed script on 2,500 rows. Every "risk at 10M" below is a statement about the
query's shape; confirm with the EXPLAIN checklist in section 5 on the seeded database.

## 1. How the schema is managed (and what that means for indexes)

* **Postgres mode: Flyway.** `src/main/resources/db/migration/V1..V15`, run at start (`spring.flyway.enabled=true`).
  Hibernate runs with `ddl-auto=validate` and never creates or alters anything, and entity annotations such as
  `@Table(uniqueConstraints)` or `@Indexed` create **no** index in Postgres mode. An index exists only if a migration creates it.
  Migrations V1-V14 are applied somewhere and were not edited. The only change made by this review is the new `V15__login_hot_path_indexes.sql`.
* **Mongo mode** (the code default, `railcab.uam.persistence.type=mongo`) creates indexes from document annotations
  (`spring.data.mongodb.auto-index-creation=true`); the MySQL adapters exist too. This review covers the PostgreSQL
  production path only; V15 does not apply to the other two.
* `PostgresMigrationTest` runs the real scripts on an embedded PostgreSQL; it now expects version 15 and asserts that
  the V15 indexes exist and serve the predicates below. (`FlywayMigrationValidationTest`, which replays the scripts on H2, already
  fails on a clean checkout because H2 cannot run the Postgres-only SQL of the earlier migrations; V15 does not change that.)

## 2. Findings per query

Legend: **OK** = an index supports it; **FIXED** = missing index, added in V15; **RISK** = not fixed, see section 4.
SQL is as generated for the Spring Data finders (captured from the real repository interface, see section 6).

| # | Path | Query shape | Index before V15 | At 10M rows | Verdict |
|---|---|---|---|---|---|
| 1 | Login: user by user name | `findByUserNameIgnoreCase` -> `where upper(user_name) = upper(?)` | none usable (`uk_uam_user_user_name` is on the raw column) | sequential scan of `uam_users` on every login | **FIXED** `idx_uam_users_user_name_upper` |
| 2 | Login: user by email (tried after the user-name miss) | `findByEmailIgnoreCase` -> `where upper(email) = upper(?)` | none usable (`uk_uam_user_email`) | sequential scan | **FIXED** `idx_uam_users_email_upper` |
| 3a | Login: user by phone, canonical | `findByPhoneE164` -> `where phone_e164 = ?` | `uk_uam_user_phone_e164` (partial unique) | index lookup | OK |
| 3b | Login: phone, legacy fallback (no canonical match) | `findAllByPhoneNumber_Number` -> `where number = ?` | none: V14 **dropped** `unique(number)` and its index | sequential scan | **FIXED** `idx_uam_users_number` |
| 4 | Lockout check, record failure/success | `findById(identifier lower-cased)`, `save`, `deleteById` on `uam_login_attempts` | primary key | index lookup (success path also does a select-then-delete) | OK; growth RISK (section 4.6) |
| 5 | Password check: credential of a user | `findByUserIdAndProvider` -> `where user_id = ? and provider = ?` on `user_credentials` | none (only `unique(provider, provider_user_id)`) | sequential scan of a 10M-row table on every login | **FIXED** `idx_user_credentials_user_provider` |
| 6 | MFA lookup | `findByUserId` on `user_mfa` | `uk_user_provider (user_id, mfa_type)` leads with `user_id` | index range | OK |
| 7 | Membership / organization at login and refresh | `findByUserId` on `tenant_memberships`; `tenants` by id; `tenants` by code (`upper(code)`, tiny table) | `idx_tenant_memberships_user`; pk; tiny | index lookups | OK |
| 8 | Application access | `findActiveBySubjects(type, ids)` on `application_access`; `applications` by `upper(code)` | `idx_application_access_subject (subject_type, subject_id, active)`; tiny table | index lookup | OK |
| 9 | Device upsert | `findByUserId` then `save` on `user_devices` | `uk_user_device (user_id, device_fingerprint)` | index range; one row per user and browser | OK |
| 10 | Session insert (login) / lookup and update (refresh) | insert `user_sessions`; `findBySessionId`; `save` with new `refresh_token_hash` | pk + `uk_user_session_session_id` + `uk_user_session_refresh_token_hash` | 3 index inserts per login; **each refresh updates an indexed column**, so it is never a HOT update and writes new entries into the hash index | RISK (write amplification, section 4.5) |
| 11 | Refresh: token by hash | `findByTokenHash` -> `where token_hash = ?` on `refresh_tokens` | none | sequential scan of the largest table in the schema, on **every refresh** (the highest-volume write) | **FIXED** `idx_refresh_tokens_token_hash` |
| 12 | Logout / revoke all | `findByPrincipalIdAndRevokedFalse` -> `where principal_id = ? and revoked = false` | none | sequential scan | **FIXED** `idx_refresh_tokens_principal` |
| 13 | Refresh token rotate / issue | update old row (`revoked`, `revoked_reason`, `last_used_at`, ...), insert new row | pk, `uk_refresh_token_id`, + V15 `token_hash`, `principal_id` | the unindexed-column update can be HOT; each insert maintains 4 indexes; **rows are never deleted** | RISK (growth, section 4.4) |
| 14 | `user_login_security` update on every login | `findByUserId` then `save` | `uk_user_login_security_user_id` | one row per user updated per login; updated columns are not indexed so HOT is possible if the page has room | RISK (bloat, fillfactor, section 4.5) |
| 15 | Auth audit inserts, in the login request thread | insert `audit_logs` (pk is a random UUID string), insert `uam_auth_audit_events` (pk + `created_at` index) | pk; `idx_uam_auth_audit_events_created_at` | 2 synchronous inserts per login/failure; random-key pk on `audit_logs`; **no purge** | RISK (growth and latency, section 4.4) |
| 16 | My privileges | `uam_users` by id; per role `findByCodeIgnoreCase`; role permissions; `permissions` `code IN (...)` | pk; role/permission tables are small (`upper(code)` scan of a small table is cheap); `uk_permission_code` | 2 + roles statements per call, no cache; fine while roles/permissions stay small | OK |
| 17 | Staff list: page | `findAll(Pageable)` -> `select ... order by user_name limit ? offset ?` and `select count(*) from uam_users` | sort served by `uk_uam_user_user_name`; count needs a full index/heap scan | count is a full scan on **every page request**; `page` is not capped, so `offset` can be any depth | RISK (section 4.1) |
| 18 | Staff search / filtered list | `lower(col) like '%term%'` OR-ed over 7 columns, optional `status`, role (`member of roles`), `tenant_id = ? or tenant_id is null`, created range; plus a count | none can serve a leading-wildcard LIKE, an `OR` across columns, `tenant_id` (no index at all on `uam_users.tenant_id`) or `uam_user_roles.role_code` | sequential scan and a second one for the count | RISK (section 4.1) |
| 19 | Directory by role (sibling services) | same specification: role + ACTIVE + optional text | no index on `uam_user_roles.role_code` | scans `uam_user_roles` for a common role; limit 100 does not help the count | RISK (section 4.2) |
| 20 | Membership list / seat count of an organization | `findByTenantId(pageable)`, `countByTenantIdAndStatus` | `idx_tenant_memberships_tenant (tenant_id, status)`, `uk_tenant_membership` | one organization with millions of members: OFFSET paging and a counting scan | RISK (section 4.3) |
| 21 | Gateway: user status | `GatewayUamUserEntity` read by primary key, cached 60 s / 10,000 users per instance | pk | index lookup, but at the assumed rates **tens of thousands per second** (`capacity-model.md` 4.5) | RISK (cache size) |
| 22 | Unbounded reads | `UamUserService.list(ListInput)` -> `findAll(Sort)` returns **all users**; no caller was found in the main code | - | would load 10M rows into memory if ever called | RISK (latent) |

Registration (`existsByUserNameIgnoreCase`, `findByUserNameIgnoreCase` for the duplicate check) shares queries 1-3 and gains from V15.

## 3. What V15 adds

`src/main/resources/db/migration/V15__login_hot_path_indexes.sql` (plain `CREATE INDEX IF NOT EXISTS`, six indexes):

| Index | Serves | Cost |
|---|---|---|
| `idx_uam_users_user_name_upper (upper(user_name))` | query 1 (and registration) | one entry per user (~50-60 bytes + overhead each) |
| `idx_uam_users_email_upper (upper(email))` | query 2 | same; NULL emails take no useful space |
| `idx_uam_users_number (number)` | query 3b | same |
| `idx_user_credentials_user_provider (user_id, provider)` | query 5 | same |
| `idx_refresh_tokens_token_hash (token_hash)` | query 11 | 64-character hex per token row; the table grows without bound until a purge exists |
| `idx_refresh_tokens_principal (principal_id)` | query 12 | per token row |

They are non-unique, so they cannot fail on existing data and change no behaviour. Why they are low-risk: they add
write cost (a few index entries per user or token insert), which is small next to the scans they remove; their size is
a few GB in total at 10M users (`capacity-model.md` working set).

**Applying V15 to a database that already holds millions of rows.** A plain `CREATE INDEX` takes a lock that blocks
writes to the table while it builds, and Flyway runs each migration in a transaction, where `CREATE INDEX CONCURRENTLY`
is not allowed. On a large live database do this *before* deploying the release that contains V15:

```sql
-- run each statement on its own (not in a transaction block), in a quiet period; same names as V15
SET maintenance_work_mem = '1GB';
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_uam_users_user_name_upper ON uam_users (upper(user_name));
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_uam_users_email_upper ON uam_users (upper(email));
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_uam_users_number ON uam_users (number);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_credentials_user_provider ON user_credentials (user_id, provider);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_refresh_tokens_token_hash ON refresh_tokens (token_hash);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_refresh_tokens_principal ON refresh_tokens (principal_id);
-- a failed concurrent build leaves an INVALID index that IF NOT EXISTS would accept: check, drop and rebuild
SELECT c.relname FROM pg_index i JOIN pg_class c ON c.oid = i.indexrelid WHERE NOT i.indisvalid;
```

Then V15 finds the names present and does nothing. On a database that is still small, just deploy V15.

## 4. Risks that were found but not changed here

### 4.1 Staff user list and search (queries 17, 18)

* The Spring Data `Page` return type makes every list call also run `select count(*)` over the same predicate: a full
  scan of `uam_users` at 10M rows, per page view. `LIKE '%term%'` on `lower(...)` of 7 columns cannot use a b-tree; a
  filtered count scans too. `page` has no upper bound, so `OFFSET` deep paging reads and discards `page x size` rows.
* `tenant_id = ? or tenant_id is null` (the tenant scope) defeats an index even if `tenant_id` had one.
* Options, in order of effort: (a) cap `page` (for example at a few hundred) and return `Slice` without the count for the
  default screen; (b) require a non-empty search term of 3+ characters and use a trigram index
  (`CREATE EXTENSION pg_trgm; CREATE INDEX CONCURRENTLY ... USING gin (lower(user_name) gin_trgm_ops)` per searched column, or one
  combined `search_text` column) and rewrite the `OR` of columns accordingly; (c) keyset pagination (`where user_name > :last order by user_name`);
  (d) serve staff search from the existing search service (`railcab.search.*` Redis stream to SearchAPI) rather than from the login database.
  These change behaviour or API and need a decision, so they are proposals, not applied.
* The load test mix keeps these at 1% each (`INCLUDE_LIST`, `INCLUDE_SEARCH`) and times them separately.

### 4.2 Role-filtered lists (query 19)

`uam_user_roles` has `(user_id, idx)` as its primary key and nothing on `role_code`. Proposal, for when role-filtered
lists are used at scale: `CREATE INDEX CONCURRENTLY idx_uam_user_roles_role ON uam_user_roles (role_code, user_id)`. Not added: it
helps selective (staff) roles only, because roughly all 10M users share one customer role, and it costs on every user insert.

### 4.3 Membership lists of one big organization (query 20)

If most users belong to one organization, `findByTenantId` (OFFSET) and `countByTenantIdAndStatus` (counts millions of
index entries; used for the seat limit check when a member is added) are long scans. Use keyset paging and a maintained counter, or count only when a limit is configured.

### 4.4 Tables that grow without limit (queries 13, 15)

`refresh_tokens` (one row per login **and** refresh), `user_sessions`, `audit_logs`, `uam_auth_audit_events` and
`uam_login_attempts` have no purge (searched for `@Scheduled`, `deleteExpired`, retention): only `uam_otp_challenges` is purged, on insert. At the volumes in
`capacity-model.md` they add up to terabytes in the first year and make every index larger (cache misses on the
login path) and vacuum slower. Proposals:

* A scheduled, **ShedLock-protected, batched** purge (repository rule: scheduled jobs safe on several instances),
  `delete ... where ctid in (select ctid from refresh_tokens where expires_at < now() - interval '5 days' limit 10000)`,
  with `CREATE INDEX CONCURRENTLY idx_refresh_tokens_expires ON refresh_tokens (expires_at)` (and the same for `user_sessions (expires_at)`,
  `audit_logs (created_at)`), or monthly range partitions that are dropped (needs the partition key in the primary key, so a migration plan).
* Retention for the audit tables is a policy decision with the audit owner (`capacity-model.md` assumes 180 days only to size it).
* Make the two audit inserts at login asynchronous, or batch them, to take them off the login latency (a design change).

### 4.5 Hot-row updates, bloat and write amplification (queries 10, 13, 14)

* `user_login_security` (per user, updated at every login), `user_devices` (updated at every login), `user_sessions` and the revoked
  `refresh_tokens` row (updated at every refresh) create dead tuples. The rows are not hot in the lock sense (one user, one row) but they bloat tables.
  HOT updates (no index entry written) need free space in the page and no indexed column changing. Proposal (needs a
  maintenance window per table, `VACUUM FULL`/`pg_repack` to apply to existing rows):
  `ALTER TABLE user_login_security SET (fillfactor = 80, autovacuum_vacuum_scale_factor = 0.02);` and the same for `user_devices`, `refresh_tokens`, `user_sessions`.
* `user_sessions.refresh_token_hash` is unique-indexed **and** rewritten on every refresh, so every refresh adds entries to two unique indexes and cannot be HOT. If the session no longer needs the hash (the refresh token row has it), stop storing it there; that is an application change.
* Autovacuum defaults (scale factor 0.2) wait for 20% dead rows: on a 100M-row table that is 20M dead tuples. Lower the per-table factors for the big tables; watch `n_dead_tup` (`post_seed.sql` prints it).

### 4.6 Lockout table keys (query 4)

`uam_login_attempts` is keyed by whatever identifier was typed, lower-cased. A row is deleted only when the same identifier tries again after expiry
or logs in successfully, so unauthenticated guessing creates rows nobody deletes. Add a purge on `expires_at` (index
`uam_login_attempts (expires_at)`), and note the endpoint is permit-all.

### 4.7 Other

* Case-insensitive lookups use `upper()`; if the columns ever move to `citext` or a case-insensitive collation, revisit V15.
* Enable `pg_stat_statements` in the load environment and in production; it is the cheapest way to see which of the queries above is the real cost.
* `login` tries user name, then email, then phone for **every** identifier. A phone number typed as the identifier still pays the two misses first
  (cheap with V15). A caller that states the identifier type would remove them; that is an API change.

## 5. EXPLAIN checklist (run on the seeded 10M-user database, after `seed/post_seed.sql`)

Use real values: `psql "$UAM_TEST_DB_URL" -X`, then (user 4242 exists in the seed; `lt_user_00004242`, `lt-u-00004242`, `+919000004242`).
Always `EXPLAIN (ANALYZE, BUFFERS)`; wrap writes in `BEGIN; ... ROLLBACK;`. Expect "shared hit" counts in the single digits to low tens for an index lookup, not thousands.

```sql
\set uname 'lt_user_00004242'
\set uid   'lt-u-00004242'
\set phone '+919000004242'

-- 1, 2: login lookups. Expect: Index Scan using idx_uam_users_user_name_upper / idx_uam_users_email_upper. FAIL if Seq Scan.
EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM uam_users WHERE upper(user_name) = upper(:'uname');
EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM uam_users WHERE upper(email) = upper('lt00004242@loadtest.invalid');
-- 3a, 3b: phone. Expect uk_uam_user_phone_e164 and idx_uam_users_number.
EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM uam_users WHERE phone_e164 = :'phone';
EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM uam_users WHERE number = '9000004242';
-- the "identifier not found" path pays all three: it must stay in the low milliseconds
EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM uam_users WHERE upper(user_name) = upper('no.such.user');
-- roles of the user (EAGER element collection): primary key (user_id, idx)
EXPLAIN (ANALYZE, BUFFERS) SELECT role_code FROM uam_user_roles WHERE user_id = :'uid' ORDER BY idx;
-- 5: credential. Expect idx_user_credentials_user_provider.
EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM user_credentials WHERE user_id = :'uid' AND provider = 'LOCAL';
-- 6, 7, 8, 9: Expect index scans on uk_user_provider, idx_tenant_memberships_user, idx_application_access_subject, uk_user_device.
EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM user_mfa WHERE user_id = :'uid';
EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM tenant_memberships WHERE user_id = :'uid';
EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM application_access WHERE subject_type = 'ROLE' AND subject_id = 'CUSTOMER' AND active = true;
EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM user_devices WHERE user_id = :'uid';
-- 11, 12 (after a load run has created tokens): Expect idx_refresh_tokens_token_hash and idx_refresh_tokens_principal.
EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM refresh_tokens WHERE token_hash = (SELECT token_hash FROM refresh_tokens LIMIT 1);
EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM refresh_tokens WHERE principal_id = :'uid' AND revoked = false;
-- 4: lockout is a primary-key lookup
EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM uam_login_attempts WHERE username = :'uname';
-- 10, 14: writes. Look at the number of indexes touched and "Heap Fetches"/HOT in pg_stat_user_tables.n_tup_hot_upd afterwards
BEGIN;
EXPLAIN (ANALYZE, BUFFERS) UPDATE user_login_security SET successful_login_count = coalesce(successful_login_count, 0) + 1 WHERE user_id = :'uid';
ROLLBACK;

-- 17, 18: the staff screens. These are EXPECTED to be slow; record the numbers, they justify section 4.1.
EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM uam_users ORDER BY user_name LIMIT 50 OFFSET 0;
EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM uam_users ORDER BY user_name LIMIT 50 OFFSET 5000000;     -- deep page
EXPLAIN (ANALYZE, BUFFERS) SELECT count(*) FROM uam_users;                                          -- the Page count
EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM uam_users WHERE lower(user_name) LIKE '%04242%' OR lower(email) LIKE '%04242%' LIMIT 20;
EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM uam_users WHERE tenant_id = 'loadtest' OR tenant_id IS NULL ORDER BY user_name LIMIT 50;
EXPLAIN (ANALYZE, BUFFERS) SELECT u.* FROM uam_users u WHERE :'uid' IS NOT NULL AND 'CUSTOMER' IN (SELECT role_code FROM uam_user_roles r WHERE r.user_id = u.id) LIMIT 100;

-- 20: membership list and seat count of the big organization
EXPLAIN (ANALYZE, BUFFERS) SELECT count(*) FROM tenant_memberships WHERE tenant_id = 'loadtest' AND status = 'ACTIVE';
```

Whole-database checks (during and after a load run):

```sql
-- queries that scan big tables: any row here with seq_scan growing on a 10M-row table during the test is a finding
SELECT relname, seq_scan, seq_tup_read, idx_scan, n_live_tup, n_dead_tup, n_tup_hot_upd, n_tup_upd
FROM pg_stat_user_tables ORDER BY seq_tup_read DESC LIMIT 15;
-- top statements by total time (needs pg_stat_statements)
SELECT calls, round(mean_exec_time::numeric, 2) AS mean_ms, round(total_exec_time::numeric) AS total_ms, left(query, 120) AS query
FROM pg_stat_statements ORDER BY total_exec_time DESC LIMIT 15;
-- unused indexes (after a representative run)
SELECT relname, indexrelname, idx_scan, pg_size_pretty(pg_relation_size(indexrelid)) FROM pg_stat_user_indexes WHERE idx_scan = 0 ORDER BY pg_relation_size(indexrelid) DESC;
-- invalid indexes left by a failed CONCURRENTLY build
SELECT c.relname FROM pg_index i JOIN pg_class c ON c.oid = i.indexrelid WHERE NOT i.indisvalid;
-- cache hit ratio of the login-path tables
SELECT relname, heap_blks_hit::float / nullif(heap_blks_hit + heap_blks_read, 0) AS heap_hit,
       idx_blks_hit::float / nullif(idx_blks_hit + idx_blks_read, 0) AS idx_hit
FROM pg_statio_user_tables WHERE relname IN ('uam_users','user_credentials','refresh_tokens','tenant_memberships');
```

Pass criteria for the login path: queries 1-12 are index scans with small `Buffers` and sub-millisecond to few-millisecond
execution on a warm cache; a `Seq Scan` on `uam_users`, `user_credentials` or `refresh_tokens` is a failure.

## 6. How the query shapes were captured

`LoadTestSeedAndLoginQueryShapeTest` (src/test, users/repository/jpa) builds the real `UamUserJpaSpringRepository` on an embedded
PostgreSQL, records the SQL Hibernate sends and asserts these fragments, so the V15 indexes cannot silently stop matching:

```
findByUserNameIgnoreCase   ... from uam_users uue1_0 where upper(uue1_0.user_name)=upper(?)
findByEmailIgnoreCase      ... from uam_users uue1_0 where upper(uue1_0.email)=upper(?)
findByPhoneE164            ... from uam_users uue1_0 where uue1_0.phone_e164=?
findAllByPhoneNumber_Number ... from uam_users uue1_0 where uue1_0.number=?
(each user load)           select ... from uam_user_roles r1_0 where r1_0.user_id=?      -- EAGER role collection, one extra statement
```

The finders of the other tables (credentials, refresh tokens, sessions, devices, MFA, memberships, application access) are
plain equality derived queries read from their Spring Data interfaces; their SQL text was not captured. The list and search
SQL comes from reading `UamUserJpaRepositoryAdapter.specification(...)`, also not captured. The same test runs the seed
script's SQL on 2,500 rows and reads a seeded user back through the entity.
