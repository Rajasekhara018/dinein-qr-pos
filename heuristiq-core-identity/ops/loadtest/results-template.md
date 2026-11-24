# Load test result (copy this file per run)

Everything below is a MEASUREMENT of one specific run. Do not fill a field from memory or from the model.
Leave a field blank if it was not observed. A result without the limiting resource is not usable for sizing.

## Run

| Field | Value |
|---|---|
| Date / who ran it | |
| Script and command line (exact, without the password) | |
| k6 version, load generator host (cores, RAM, region) | |
| Git commit of Rider_UAM under test | |
| Duration, steps | |

## Environment

| Field | Value |
|---|---|
| UAM instances in the test (a breakpoint number is for ONE instance) | |
| Instance size: CPU limit (cores), memory limit, JVM options (heap) | |
| Hikari pool size (`UAM_POSTGRES_MAX_POOL_SIZE`) / min idle | |
| bcrypt strength (`UAM_PASSWORD_BCRYPT_STRENGTH`), pool queue, timeout | |
| Access token minutes / refresh days | |
| Postgres version, instance size (cores, RAM), storage type, `shared_buffers`, `max_connections` | |
| Dataset: users seeded, credentials in the CSV, tables analyzed after seed (y/n), indexes present (V15 applied y/n) | |
| Network between k6, UAM and Postgres (same AZ / RTT ms) | |
| Anything else running on the same hosts | |

## Result

| Field | Value |
|---|---|
| Mode (`BP_MODE`): login / refresh / lookup / mixed | |
| **rps at breakpoint** (highest step that passed, offered rate, one instance) | |
| Achieved rps at that step (from the step table) | |
| p50 / p95 / p99 at that step (ms), overall and for login | |
| Failed request rate at the first failing step, and the dominant status codes (e.g. 503, 500, 0) | |
| `dropped_iterations` at breakpoint (and was the generator saturated? y/n) | |
| **Bottleneck** (what failed first, in one sentence) | |
| **Limiting resource** (UAM CPU / bcrypt workers / Hikari pool / Postgres CPU / Postgres IO / Postgres locks / network / k6 generator) | |
| Evidence (dashboard panel and value at the breakpoint: CPU %, Hikari active/pending, bcrypt 503s, DB active sessions, top query) | |

## Cost parameters read off this run (feed the capacity model)

| Parameter (scenario.json path) | Measured | How measured |
|---|---|---|
| `cost_per_request.login_cpu_ms` (CPU ms per login = cores x CPU% x 1000 / logins per second, login-only run) | | |
| `cost_per_request.other_cpu_ms` (same for the lookup run) | | |
| `cost_per_request.db_hold_ms.login` / `refresh` / `me_privileges` / ... (Hikari usage time, or `pg_stat_statements` total time per request) | | |
| `instance.sustainable_rps_per_instance` = rps at breakpoint x safety margin of | | |
| `postgres.max_connections` | | |

## Query findings (from `pg_stat_statements` / EXPLAIN during the run)

| Statement (normalized) | Calls | Mean ms | Plan (index or Seq Scan) | Action |
|---|---|---|---|---|
| | | | | |

## Soak / spike only

| Field | Value |
|---|---|
| Soak: p95 first 15 min vs last 15 min; heap after GC start vs end; Hikari pending; dead tuples before/after (`post_seed.sql`) | |
| Spike: errors during the spike; time to recover (until p95 and error rate returned to baseline) | |

## Next action

| Field | Value |
|---|---|
| What changes next (index, pool size, cores, bcrypt cost, caching, instance count) | |
| Owner and date | |
| Re-test needed after the change (y/n) | |
