# Rider_UAM capacity model

Scenario: about 10,000,000 registered users; 30-40% active per day (3-4 million); in the busiest window 10-20% of
users active (1-2 million); production database PostgreSQL.

## 0. How to read this document

Two kinds of number appear here and they are never mixed:

| Label | Meaning |
|---|---|
| **CODE** | Read from the Rider_UAM / ApiGateway source or committed configuration defaults (a deployment may override environment variables). |
| **USER** | Stated by the product owner (population, active shares, window lengths). |
| **ASSUMPTION** | A judgement made while writing this model. It has no measurement behind it. Change it in `scenario.json`. |
| **PLACEHOLDER** | An input that **must** be replaced by a load-test measurement before the output is used for sizing (CPU per login, per-instance sustainable rps, database connection hold times, Postgres limits). |
| **MEASUREMENT** | A value taken from a real run, recorded with `ops/loadtest/results-template.md`. |

**Measurements in this repository: none.** No load environment existed when this was written (no k6, no Docker daemon,
no database host), so every output below is a function of CODE, USER, ASSUMPTION and PLACEHOLDER inputs. The tables
print instance counts for a range of per-instance rps values instead of choosing one.

Files: `scenario.json` (all inputs, each with its basis), `capacity_model.py` (calculator, standard library only),
`capacity-model-output.txt` (full output of the command below), `query-review.md` (index and query review),
`../../ops/loadtest/` (k6 scripts, seed, results template).

```
cd Rider_UAM/docs/capacity
python capacity_model.py scenario.json            # report
python capacity_model.py scenario.json --json     # same numbers as JSON
```

The calculator reads the token lifetimes, bcrypt cost, bcrypt queue and Hikari pool size from
`Rider_UAM/src/main/resources/application*.properties` (their committed defaults) and says so when the file and the
scenario disagree.

## 1. What UAM is asked to do (the request flows)

Verified in the code (class names are in `com.heuristiq.uam`):

| Flow | Endpoint | What happens | DB work (PostgreSQL mode) |
|---|---|---|---|
| Password login | `POST /api/v1/uam/identity/auth/login` (`IdentityAuthController` -> `AuthService.login` -> `LoginSessionService.issue`) | lockout check; user lookup by user name, then email, then phone (stops at the first hit); bcrypt verify on a bounded pool; MFA lookup; membership/tenant resolution; application-access resolution; device upsert; session insert; refresh-token insert; JWT mint; login-security update; 2 audit inserts | about 20 statements: reads on `uam_users` (x1-3), `uam_login_attempts` (x2), `user_credentials`, `user_mfa`, `tenant_memberships`, `tenants`, `application_access`, `applications`, `user_devices`; writes on `user_devices`, `user_sessions`, `refresh_tokens`, `user_login_security`, `uam_login_attempts` (delete), `audit_logs`, `uam_auth_audit_events` |
| OTP login | `POST /identity/auth/phone/send-otp` then `/phone/verify-otp` (same for email) | send: OTP row + NMS call; verify: the same session/token work as a login, without bcrypt of a password (OTP is hashed with a salt) | one `uam_otp_challenges` row per OTP (rows past `expires_at` are purged on each insert) plus the login writes |
| Refresh / organization switch | `POST /identity/auth/refresh` (`LoginSessionService.rotate`) | find token by SHA-256 hash, load user, resolve organization (an organization-wide account may send `X-Organization-Code`: that is the organization switch), recompute applications, insert a new `refresh_tokens` row and mark the old one revoked, update the session | about 10 statements, 3 writes; **grows `refresh_tokens` by one row per refresh** |
| Logout | `POST /identity/auth/logout` | revoke every active refresh token of the caller | select by `principal_id` + one update per token |
| Token validation | **no endpoint** | JWT HMAC signature is verified locally: in UAM by `JwtAuthenticationFilter`, and in the ApiGateway by `HmacJwtAuthProvider`. No database call. | none |
| My privileges | `GET /identity/me/privileges` (`MyPrivilegesService`) | user by id, each role by code, permissions `IN (...)`; **no cache** | 2 + number of roles statements |
| My organizations | `GET /identity/me/organizations` | memberships of the user and their tenants | 1 + number of memberships |
| User lookups (sibling services) | `GET /users/{id}`, `/users/by-phone/{phone}`, `/users/batch`, `/directory/users` | one row (or at most 100) | 1-2 |
| Staff list / search | `GET /users?page=&size=` and `?search=` (`UserController.list`) | page of users sorted by user name **plus a count over all matching rows**; search is `lower(col) LIKE '%term%'` over 7 columns | scans at 10M rows, see `query-review.md` |

### 1.1 Does anything else call UAM per request? (this decides UAM's real request rate)

* **ApiGateway verifies JWTs itself** (HMAC) and does not call UAM over HTTP per request. It does, however, **read UAM's
  tables directly** (`Rider_ApiGateway`, `identity/` and `privilege/` packages):
  * `UserStatusCacheService`: is the user ACTIVE? A primary-key read on `uam_users`, cached per user for
    **60 s, at most 10,000 users per gateway instance** (CODE: `railcab.gateway.cache.user-status-*`).
  * `RolePrivilegeCacheService`: privileges of each role, cached **30 s, 1,000 roles** (a handful of rows; negligible).
* The ApiGateway's rate limiter for the auth paths is 5 requests/s with a burst of 10, keyed by source IP for the public login paths
  (`railcab.gateway.rate-limit.auth-*`). Load tests must go to UAM directly or accept this limit.
* Rider_Web calls `/identity/me/privileges` and `/identity/me/organizations` at app load and `/identity/auth/refresh`
  to renew the session; the Flutter customer app has a token-refresh service. Sibling services (Wallet, Train, Workflow,
  Onboarding, Customer, Admin) call user lookups and the directory.
* No token-introspection, "validate" or "permission check" call from other services into UAM was found.

### 1.2 Token and password settings that drive the volumes (CODE)

* Access token (JWT): **60 minutes** (`railcab.uam.auth.expiry-minutes`).
* Refresh token: **30 days** (`railcab.uam.auth.refresh-expiry-days`), rotated on every refresh.
* Password hash: **bcrypt, cost 11** (`railcab.uam.auth.password.bcrypt-strength`; the code comment says about 202 ms per
  operation on its reference hardware, which is a claim to verify, not a measurement). bcrypt runs on a fixed pool of
  `availableProcessors()` platform threads, a queue of **64**, and a **2 s** timeout after which the login gets HTTP 503.
  Each +1 of cost doubles the CPU cost.
* Database pool: **5 connections per instance** (`UAM_POSTGRES_MAX_POOL_SIZE`). This is small on purpose and is a
  capacity parameter: with 5 connections and a 30 ms hold per login, one instance can start at most about 5 / 0.030 = 167
  logins/s from a database point of view (the model checks this with Little's law).

### 1.3 Hot path, in order of cost per request

1. `POST /identity/auth/login`: CPU-dominant (bcrypt) and about 20 statements, 7 of them writes. Cheap in count, dominant in cost.
2. `POST /identity/auth/refresh`: about 10 statements, 3 writes; the **highest-volume** state-changing call.
3. `GET /identity/me/privileges` and `/me/organizations`: highest-volume reads, a few statements each, uncached.
4. Sibling-service lookups: single-row reads.
5. Staff list/search: negligible in rate, but each call is a scan at 10M rows (`query-review.md`).

## 2. Requests per active user, and where each number comes from

For a window of `m` minutes and `A` active users (peak hour: m = 60):

| Action | Per active user | Basis |
|---|---|---|
| Full logins | `login_fraction` = 0.25 per hour, 0.15 per 15-minute burst | ASSUMPTION. Most returning users hold a 30-day refresh token and only refresh; the share that logs in inside the busiest window is unknown. This is the most sensitive input of the model (it drives CPU). |
| Refreshes | `m / access_ttl` x participation = 60 / 60 x 1.0 = **1.0 per hour**, 0.25 per 15 min | TTL is CODE (60 min); that every active client keeps its session alive by refreshing once per TTL is an ASSUMPTION. |
| `me/privileges` | 0.6 per hour | ASSUMPTION (web screens only). |
| `me/organizations` | 0.6 per hour | ASSUMPTION. |
| User lookups by sibling services | 0.2 per hour | ASSUMPTION. |
| Logout | 0.1 per hour | ASSUMPTION. |
| Token validation calls to UAM | **0** | CODE: validation is local (section 1.1). |

That is **2.75 requests per active user per hour** on UAM at the peak-hour defaults (0.25 + 1.0 + 0.6 + 0.6 + 0.2 + 0.1),
of which 0.25 are logins and 1.0 refreshes, plus staff list/search (3,000 staff x 30 calls/hour, ASSUMPTION). The gateway
side (60 requests/user/hour, ASSUMPTION) is modelled separately as database reads, section 5.

## 3. Formulas

```
A                    = registered x peak_active_pct
count(endpoint)      = A x per-user rate x (m / 60)           logins: A x login_fraction;   refresh: A x m / access_ttl
avg rps              = count / (m x 60)
peak rps             = avg rps x intra_window_peak_factor      (1.5, ASSUMPTION)
CPU cores needed     = (peak login rps x login_cpu_ms + peak other rps x other_cpu_ms) / 1000
instances by CPU     = ceil(cores needed / (cores per instance x cpu_target_utilization))
instances by rps     = ceil(peak total rps / (sustainable_rps_per_instance x (1 - headroom)))
DB connections in use= sum over endpoints (peak rps x connection hold seconds)         (Little's law)
instances by pool    = ceil(connections in use / pool size per instance)
required instances   = max(by rps, by CPU, by pool)
connections reserved = instances x pool size   must be <= (max_connections - reserved for others) x usable_fraction
```

Gateway user-status reads (`gateway_status_lookups`): with `N` distinct active users per gateway instance, cache size
`C`, TTL `T` and `R` requests per second per instance: `N <= C`: `min(R, N / T)` reads/s; `N > C`: `R x (1 - C / N)`.
This is an approximation of Caffeine's eviction, adequate for an order of magnitude.

## 4. Worked example for the stated scenario (parameter-driven, not measured)

Interpretation of "10-20% active at peak": **low 10% = 1.0M, expected 15% = 1.5M (midpoint, our choice), high 20% = 2.0M
users active in the busiest hour**; and a 15-minute burst in which 15% of them log in.

### 4.1 Arithmetic for the expected case, peak hour (every step)

```
A                  = 10,000,000 x 15%                         = 1,500,000 active users
logins             = 1,500,000 x 0.25                         =   375,000 per hour  -> 375,000 / 3600 = 104.2/s average, x 1.5 = 156.2/s peak
refreshes          = 1,500,000 x (60 / 60) x 1.0              = 1,500,000 per hour  -> 416.7/s average, 625.0/s peak
me/privileges      = 1,500,000 x 0.6                          =   900,000 per hour  -> 250.0/s average, 375.0/s peak
me/organizations   = same                                      -> 250.0/s average, 375.0/s peak
user lookups       = 1,500,000 x 0.2                          =   300,000 per hour  ->  83.3/s average, 125.0/s peak
logouts            = 1,500,000 x 0.1                          =   150,000 per hour  ->  41.7/s average,  62.5/s peak
staff list / search= 3,000 x 20 and 3,000 x 10                =    60,000 / 30,000 per hour -> 25.0 / 12.5 peak
total              = 4,215,000 requests per hour              -> 1,170.8/s average, 1,756.2/s peak

CPU                = 156.2 x 0.250 s + (1,756.2 - 156.2) x 0.003 s = 39.1 + 4.8 = 43.9 cores     (login_cpu_ms 250 and other_cpu_ms 3 are PLACEHOLDERS)
instances by CPU   = ceil(43.9 / (4 cores x 0.7)) = ceil(15.7) = 16
```

The CPU line is the point to take from this example: under the placeholder costs a login is roughly eighty times a refresh
or a lookup (250 ms against 3 ms), so **the number of instances is set by logins per second unless the per-instance rps is very low**, and the
login share (an ASSUMPTION) is what moves it. Replace `login_cpu_ms` with the measured value.

### 4.2 Instances required vs the unknown sustainable rps per instance

Output of `capacity_model.py scenario.json` (summary section, verbatim):

```
scenario / window               users  peak rps  logins/s  by CPU     50    100    200    400    800
                                                                   <- instances required at that sustainable rps per instance
low 10% / peak_hour             1.00M      1183       104      11     34     17     12     12     12
low 10% / burst_15m             1.00M      1329       250      24     38     24     24     24     24
expected 15% / peak_hour        1.50M      1756       156      16     51     26     16     16     16
expected 15% / burst_15m        1.50M      1975       375      36     57     36     36     36     36
high 20% / peak_hour            2.00M      2329       208      21     67     34     21     21     21
high 20% / burst_15m            2.00M      2621       500      47     75     47     47     47     47
  required = max(ceil(peak rps / (rps x (1 - headroom))), instances by CPU, instances by DB pool)
  headroom 0.3, cpu target 0.7, cores/instance 4, login_cpu_ms 250 (PLACEHOLDER), pool/instance 5
```

Reading it: the right-hand columns are instances for a sustainable rps per instance of 50 / 100 / 200 / 400 / 800. Where
a row stops decreasing (for example 16 for the expected case), the limit is not rps but login CPU (the "by CPU" column)
or the database pool; raising the per-instance rps does not help until bcrypt cost, cores per instance or the login
share changes. Do not pick a column: measure `instance.sustainable_rps_per_instance` with `breakpoint.js` and re-run.

### 4.3 Per-endpoint rates, expected case

```
SCENARIO EXPECTED: 15% active = 1.50M users, window peak_hour (60 min)
------------------------------------------------------------------------
  endpoint            requests in window     avg rps    peak rps
  login                          375,000       104.2       156.2
  refresh                      1,500,000       416.7       625.0
  logout                         150,000        41.7        62.5
  me_privileges                  900,000       250.0       375.0
  me_organizations               900,000       250.0       375.0
  user_lookup                    300,000        83.3       125.0
  user_list                       60,000        16.7        25.0
  user_search                     30,000         8.3        12.5
  TOTAL                        4,215,000      1170.8      1756.2
  CPU cores needed at peak (login_cpu_ms/other_cpu_ms PLACEHOLDERS): 43.9  -> instances by CPU: 16
  max logins/s one instance can take (cores x util / login_cpu): 11.2   (bcrypt queue 64, timeout 2000 ms)
  DB connections in use at peak (Little's law, hold-time PLACEHOLDERS): 64.1  -> instances to cover with pool size 5: 13
  Postgres connections available to UAM: 119  -> at most 23 instances before max_connections is the limit
  Gateway user-status reads of uam_users (PK lookups, NOT UAM requests): 24500/s (500,000 distinct users per gateway instance vs cache 10,000)

  Instances required as a function of the UNKNOWN sustainable rps per instance:
   rps/instance   by rps  required  DB conns  fits Postgres
             50       51        51       255  NO
            100       26        26       130  NO
            200       13        16        80  yes
            400        7        16        80  yes
            800        4        16        80  yes
  required = max(by rps, by CPU, by DB pool)
```

### 4.4 Database connections

At the placeholder hold times the connections in use at the expected peak come to about 64 (printed above), against
**5 per instance** (CODE), which is a floor of 13 instances by pool against 16 by CPU at the placeholder values; both floors move with the
measured hold times and login cost. `instances x 5` must also fit under Postgres `max_connections` (200 is a PLACEHOLDER; the
model leaves 60 for the gateway and other clients and uses 85% of the rest, so about 119 connections for UAM, i.e.
at most 23 instances at pool size 5). Either raise the pool per instance (and the DB) or add a pooler (PgBouncer in
transaction mode) before adding instances; the sizing answer depends on the measured hold times.

### 4.5 The gateway's reads of `uam_users` (not UAM requests, but UAM's database)

With 60 requests per active user per hour (ASSUMPTION), 3 gateway instances and the shipped cache (10,000 users per
instance, 60 s), the expected case has about 500,000 distinct users per gateway instance, so the cache hit ratio is
about 2% and the model prints about **24,500 primary-key reads per second** on `uam_users`, i.e. more than ten times UAM's own
request rate. This is CODE-derived (cache size and TTL) with an ASSUMED request rate. If the assumption is close,
the shipped `user-status-max-size` is far too small for this population. Measure the gateway's real
per-user request rate and either raise the cache size (memory per entry is small), extend the TTL, or push status changes
by event; this is a gateway change and outside this work.

## 5. Storage, growth and working set

Bytes per row are ASSUMPTIONS (`scenario.json storage.tables`); replace them with `post_seed.sql` output. Event volumes
use 0.5 full logins and 90 active minutes per daily active user per day (ASSUMPTIONS). No purge job exists in UAM for
`user_sessions`, `refresh_tokens`, `audit_logs`, `uam_auth_audit_events` or expired `uam_login_attempts`
(searched `@Scheduled`, `deleteExpired`, retention; only `uam_otp_challenges` purges), so "unbounded" is today's behaviour.

```
dau_high: DAU = 4.00M
  table                     B/row  rows (now or after 1y)  GB then   GB/yr  GB 5y unbounded  GB w/ retention
  uam_users                   900              10,000,000      9.0     1.4             18.1             18.1
  uam_user_roles              120              10,000,000      1.2     0.2              2.4              2.4
  user_credentials            480              10,000,000      4.8     0.7              9.7              9.7
  tenant_memberships          370              10,000,000      3.7     0.6              7.4              7.4
  password_history            320              10,000,000      3.2     0.5              6.4              6.4
  user_login_security         450               8,000,000      3.6     0.5              7.2              7.2
  user_devices                550              12,000,000      6.6     1.0             13.3             13.3
  user_sessions               580             730,000,000    423.4   423.4           2117.0            104.4
  refresh_tokens              600           2,920,000,000   1752.0  1752.0           8760.0            168.0
  audit_logs                  560             803,000,000    449.7   449.7           2248.4            221.8
  uam_auth_audit_events       320             803,000,000    257.0   257.0           1284.8            126.7
  uam_login_attempts          170              73,000,000     12.4    12.4             62.0              0.0
  TOTAL                                                     2926.5  2899.3          14536.8            685.5
```

Reading it:

* The user-owned tables (`uam_users`, credentials, memberships, roles, history, devices) are tens of GB in total and grow with registrations, about 15% a year here.
* Event tables are what grow without limit: with no retention the event tables reach roughly 2-3 TB in the first year and 11-15 TB in five, almost all of it
  `refresh_tokens` (one row per login **and per refresh**), `audit_logs`, `uam_auth_audit_events` and `user_sessions`.
* The "GB w/ retention" column applies `storage.proposed_retention_days` (ASSUMPTION: sessions 90 d, refresh tokens 35 d,
  audit 180 d). That is a design proposal to decide, not existing behaviour; it needs a purge job (batched deletes or partition drops) and, for the audit tables, a policy with the audit owner.
* Refresh tokens need no more than the 30-day lifetime plus a margin; sessions older than the longest refresh lifetime are dead.

Working set of the login path (indexes of all users plus the heap pages of the active users), as printed by the calculator:

```
WORKING SET of the login path (indexes of all users + heap of the active users), GB:
  low       indexes 9.1 + heap 1.4..14.1  => 10.5 .. 23.2 GB  (database RAM placeholder 64 GB)
  expected  indexes 9.1 + heap 2.1..14.1  => 11.2 .. 23.2 GB  (database RAM placeholder 64 GB)
  high      indexes 9.1 + heap 2.8..14.1  => 11.9 .. 23.2 GB  (database RAM placeholder 64 GB)
```

The lower bound assumes the active users' rows are contiguous; the upper bound assumes each active user's row sits on its own 8 KB page, which is
closer to the truth for UUID-keyed rows inserted over time. Compare with the database RAM (PLACEHOLDER 64 GB): the index part (about 9 GB with the V15 indexes) should fit in `shared_buffers` plus the OS cache so that logins do not
read from disk; measure with `pg_statio_user_tables` and the cache-hit ratio during the soak.

## 6. Sensitivities (what moves the answer, in order)

1. **Login share and bcrypt cost per login.** CPU is linear in both. Cost 12 doubles it, cost 10 halves it.
2. **Whether every client refreshes every 60 minutes.** Refresh volume is the largest request count and the largest source of `refresh_tokens` growth.
3. **Pool size per instance and Postgres `max_connections`.** Five connections per instance caps database throughput per instance.
4. **The gateway status cache size** (section 4.5).
5. **Intra-window peak factor** (1.5): arrivals are not uniform inside the hour.
6. **Staff list/search at 10M rows**: tiny rate, but each call is a scan; keep them off the login database or fix the queries (`query-review.md`).

## 7. Limits of the model

Average-based (Little's law), uniform arrivals inside the window, no retries or retry storms, no queueing delay (it
gives capacity, not latency), no caching in UAM (there is none on the hot path), one role per user, one tenant, the
password flow only (OTP login adds an NMS call and SMS cost, not modelled), no MFA (an authenticator check adds CPU),
no Keycloak/OIDC or Google login (they add outbound calls through Resilience4j bulkheads of 10), autovacuum and
checkpoint effects ignored. It is a planning tool whose inputs must be replaced by measurements one by one.

## 8. Replacing placeholders with measurements

1. Seed and run the tests in `ops/loadtest/README.md` against one instance sized like production.
2. Fill `ops/loadtest/results-template.md` for each run.
3. Edit `scenario.json`: set `instance.sustainable_rps_per_instance` (rps at breakpoint x your safety margin), the
   measured `cost_per_request.*`, the Postgres limits and the measured row sizes; set each changed leaf's `basis` to `MEASUREMENT`.
4. `python capacity_model.py scenario.json` and read the instance count from the row of your scenario.
