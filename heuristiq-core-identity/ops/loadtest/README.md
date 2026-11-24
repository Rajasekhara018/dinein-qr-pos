# Rider_UAM load tests (k6)

Scripts that drive UAM at a chosen request rate, a seed that builds a realistic 10-million-user PostgreSQL
database, and a results template whose numbers feed the capacity model in `../../docs/capacity/`.

**Status: written and statically checked only.** No k6 binary and no load environment were available when this was
written, so no script has been run against a real UAM and **there are no measured results in this repository**. See
"What was and was not verified" at the end.

## Contents

| Path | Purpose |
|---|---|
| `login.js` | Arrival-rate load of full logins (password, or OTP with `AUTH_MODE=otp`) |
| `refresh.js` | Arrival-rate load of token refresh (rotating chains, one per VU) |
| `validate-and-lookup.js` | Authenticated reads: privileges, organizations, user lookups, list, search |
| `mixed-peak.js` | The whole mix in the proportions of `docs/capacity/scenario.json` (`loadtest.mix`) |
| `soak.js` | Long constant-rate mix (leaks, bloat, slow degradation) |
| `spike.js` | Sudden jump and recovery |
| `breakpoint.js` | Stepped ramp until a limit is breached; yields the sustainable rate of ONE instance |
| `lib/` | `config.js` (all env knobs), `client.js` (API calls, per-endpoint tags), `mix.js`, `thresholds.js`, `summary.js`, `data.js` |
| `seed/` | `seed_loadtest.sql`, `post_seed.sql`, `export_credentials.sh`, `cleanup_loadtest.sql` |
| `results-template.md` | One filled copy per run; paste the numbers into `scenario.json` |
| `data/`, `results/` | Git-ignored output (credentials sample, k6 result files) |

## Requirements

* k6 0.50 or newer (uses `k6/execution` vu tags and the Prometheus remote-write output). Install: https://grafana.com/docs/k6/latest/set-up/install-k6/
* `psql` (PostgreSQL client tools), bash (Git Bash or WSL on Windows) for `export_credentials.sh`
* A way to make a bcrypt hash (below), Python 3 for the calculator
* A **test** PostgreSQL database and a UAM instance pointed at it. Never the production database.

## 1. Stack under test

Run UAM against its own PostgreSQL database (this is the production persistence mode), on hardware like production,
and keep the load generator on a separate machine.

```
SPRING_PROFILES_ACTIVE=postgres
UAM_POSTGRES_URL=jdbc:postgresql://<db-host>:5432/uam_loadtest
UAM_POSTGRES_USERNAME=...   UAM_POSTGRES_PASSWORD=...
UAM_POSTGRES_MAX_POOL_SIZE=5          # the shipped default; this is a capacity parameter, vary it deliberately
JWT_SECRET=<any 256-bit test secret>   GATEWAY_FORWARD_KEY=<test value>   NMS_INTERNAL_API_KEY=<test value>
SERVICE_DISCOVERY_ENABLED=false        # and EUREKA_URL=http://localhost:9100/eureka/ if startup still asks for it
```

Start UAM once so Flyway creates the schema (V1 to V15). Then seed. Notes:

* Test **UAM directly** (`BASE_URL=http://<uam>:9103`). Through the ApiGateway you are also testing its rate limiter
  (auth paths: `railcab.gateway.rate-limit.auth-replenish-rate=5`, burst 10, keyed by source IP for public paths) and one gateway; do that as a separate,
  explicit test.
* For `AUTH_MODE=otp` start UAM with `UAM_OTP_EXPOSE_OTP=true` (the code is returned in the send response) and make NMS
  reachable or stubbed. UAM allows 5 OTP sends per phone per hour and one per 30 s, so OTP login needs a large credentials file.
* Login lockout (5 failures, 15 minutes) cannot trigger here: every request carries the right password.

## 2. Password hash (documented command, not a guess)

UAM stores bcrypt hashes and verifies at the cost stored **in the hash** (`BCryptPasswordEncoder`; production creates
new hashes at cost 11, `railcab.uam.auth.password.bcrypt-strength`). Use cost 11 so a test login costs what a real one
costs. Pick a test password and generate the hash once; every seeded user shares it (a salt does not change the CPU cost).

```bash
# Option A - Apache htpasswd (apache2-utils); $2y$ is accepted by Spring's BCryptPasswordEncoder
export LOADTEST_PWHASH="$(htpasswd -bnBC 11 '' 'ChooseATestPassword' | tr -d ':\n')"

# Option B - Python
python -m pip install bcrypt
export LOADTEST_PWHASH="$(python -c "import bcrypt,sys; print(bcrypt.hashpw(sys.argv[1].encode(), bcrypt.gensalt(11)).decode())" 'ChooseATestPassword')"

echo "$LOADTEST_PWHASH" | cut -c1-7      # expect  $2y$11$  or  $2b$11$
export PASSWORD='ChooseATestPassword'    # what k6 sends; never commit it
```

Check it against UAM before seeding millions of rows: seed 1000 users (`-v n_users=1000`), then
`curl -s -X POST $BASE_URL/api/v1/uam/identity/auth/login -H 'Content-Type: application/json' -d '{"requestObject":{"username":"lt_user_00000001","password":"'"$PASSWORD"'"}}'`
must return `"success":true` with an `accessToken`.

## 3. Seed

The script is TEST ONLY and refuses to run unless **all** of these hold: `uam.loadtest_seed=allow` in the session, a
database name that does not contain `prod`/`production`/`live`, a tenant id starting with `loadtest`, a bcrypt-shaped
hash, and the UAM schema present. It writes only the organization `loadtest` and ids prefixed `lt-`; every insert is
`ON CONFLICT DO NOTHING`, so it is idempotent and re-runnable to top up.

```bash
export UAM_TEST_DB_URL='postgresql://uam_user@db-host:5432/uam_loadtest'   # password via PGPASSWORD or ~/.pgpass
cd seed
# small trial first, and time it:
PGOPTIONS="-c uam.loadtest_seed=allow" psql "$UAM_TEST_DB_URL" -X -v pwhash="$LOADTEST_PWHASH" -v n_users=200000 -f seed_loadtest.sql
# full size (default 10,000,000 users, 100,000 per batch/transaction):
PGOPTIONS="-c uam.loadtest_seed=allow" psql "$UAM_TEST_DB_URL" -X -v pwhash="$LOADTEST_PWHASH" -v n_users=10000000 -v batch=100000 -f seed_loadtest.sql
psql "$UAM_TEST_DB_URL" -X -f post_seed.sql        # VACUUM (ANALYZE) + table sizes
./export_credentials.sh 100000                      # -> data/credentials.csv (user_id,username,phone; no password)
```

Per user it writes `uam_users` (ACTIVE, unique fake phone `+91 9000000001`..), `uam_user_roles` (role `CUSTOMER`,
override with `-v role_code=...`), `user_credentials` (LOCAL, your hash) and an ACTIVE `tenant_memberships` row in the
test organization. `user_login_security`, `user_devices`, `user_sessions`, `refresh_tokens` and audit rows are created
by UAM itself at first login, which is part of what is measured.

**Estimating seed time and disk** (no figure is quoted because it depends on your hardware and Postgres settings):
run the 200,000-user trial with `\timing`, then `total time ~ trial seconds x (N / 200000)`. After `post_seed.sql` read
`bytes_per_row_total` per table and `disk ~ sum(bytes_per_row_total x rows)`; the same output replaces the
`heap_bytes`/`index_bytes` guesses in `scenario.json`. For speed during the seed only, `synchronous_commit=off`,
a large `max_wal_size` and `maintenance_work_mem` help; restore them afterwards.

**After every load test:** run `post_seed.sql` again (VACUUM ANALYZE and the dead-tuple report), because refresh-token
rotation and per-login updates create bloat that would distort the next run. To start from a clean state at 10M users,
drop and recreate the database and re-seed rather than running `cleanup_loadtest.sql` (which is for small seeds).

## 4. Run

All settings are environment variables (`-e NAME=value`); the full list with defaults is `lib/config.js`.

| Variable | Default | Meaning |
|---|---|---|
| `BASE_URL` | `http://localhost:9103` | UAM (or gateway) root |
| `PASSWORD` | required | Password of the seeded users |
| `CREDENTIALS_FILE` | `./data/credentials.csv` | From `export_credentials.sh` (relative to the script) |
| `AUTH_MODE` | `password` | `password` or `otp` |
| `TARGET_RPS`, `START_RPS` | 20, 1 | Plateau and start of the arrival rate (requests started per second) |
| `RAMP_UP`, `HOLD`, `RAMP_DOWN` | 2m, 5m, 30s | Plateau shape |
| `PRE_VUS`, `MAX_VUS` | 100, 2000 | VU pool; keep `MAX_VUS` at or below the credentials count |
| `P95_MS`, `P99_MS` | 500, 1000 | Latency limits (parameters, not measurements); `P95_LOGIN_MS`, `P95_LIST_MS`, `P95_SEARCH_MS` ... for those endpoints |
| `MAX_FAILED_RATE`, `MAX_DROPPED` | 0.01, 50 | Failure and dropped-iteration limits |
| `INCLUDE_LIST`, `INCLUDE_SEARCH` | true, true | Heavy staff screens; turn off to isolate the hot path |
| `SCENARIO_FILE`, `MIX_JSON` | `../../docs/capacity/scenario.json`, - | Source of the mix weights, or an inline override |
| `SUMMARY_FILE` | - | Also write the full summary JSON here |

Suggested order (do each against the seeded 10M-user database, one instance, after `post_seed.sql`):

```bash
cd Rider_UAM/ops/loadtest
k6 run -e PASSWORD="$PASSWORD" -e TARGET_RPS=5  -e HOLD=2m login.js                       # smoke: does the whole path work
k6 run -e PASSWORD="$PASSWORD" -e BP_MODE=login   -e BP_START=2  -e BP_STEP=2  breakpoint.js   # login limit (bcrypt-bound)
k6 run -e PASSWORD="$PASSWORD" -e BP_MODE=refresh -e BP_START=25 -e BP_STEP=25 breakpoint.js   # refresh limit
k6 run -e PASSWORD="$PASSWORD" -e BP_MODE=lookup  -e BP_START=25 -e BP_STEP=25 breakpoint.js   # read limit
k6 run -e PASSWORD="$PASSWORD" -e BP_MODE=mixed   -e BP_START=20 -e BP_STEP=20 breakpoint.js   # THE number for the model
k6 run -e PASSWORD="$PASSWORD" -e TARGET_RPS=<70% of it> mixed-peak.js                         # steady confirmation
k6 run -e PASSWORD="$PASSWORD" -e BASE_RPS=<50%> -e SPIKE_RPS=<2-3x> spike.js
k6 run -e PASSWORD="$PASSWORD" -e SOAK_RPS=<50-70%> -e SOAK_DURATION=4h soak.js
```

Output:

```bash
# JSON summary (k6's own and ours)
k6 run --summary-export results/mixed.json -e SUMMARY_FILE=results/mixed-full.json ... mixed-peak.js
# Every request as it happens, for offline analysis
k6 run --out json=results/mixed.ndjson ... mixed-peak.js
# Live into Prometheus (Prometheus must run with --web.enable-remote-write-receiver); then use the UAM dashboard
K6_PROMETHEUS_RW_SERVER_URL=http://prometheus:9090/api/v1/write K6_PROMETHEUS_RW_TREND_STATS="p(95),p(99),avg,max" \
  k6 run -o experimental-prometheus-rw ... mixed-peak.js
```

Every request carries an `endpoint` tag (`login`, `refresh`, `me_privileges`, `user_search`, ...), so every output
splits per endpoint. `login_warmup` is a VU's first sign-in and re-sign-in after a logout; it is reported but is not
part of the mix. Each script prints a per-endpoint table at the end (p50/p95/p99/failed/threshold) to paste into the
results template.

## 5. What to watch while it runs

Open the UAM Grafana dashboard `Rider_UAM/ops/grafana/uam-dashboard.json` (built separately; panel titles may differ
from the names below) next to k6. Look for the **first** resource that saturates:

| Signal (metric) | Saturation looks like | Likely limit |
|---|---|---|
| Request rate and p95/p99 per URI (`http_server_requests_seconds`, histogram enabled) | p95 climbs while rate flattens | whichever below |
| CPU (`process_cpu_usage`, container CPU) | near the limit; login p95 grows first | bcrypt (CPU-bound login) |
| bcrypt queue / HTTP 503 "Password verification is temporarily overloaded" | 503s on login | worker pool = cores, queue 64, timeout 2 s |
| Hikari (`hikaricp_connections_active`, `_pending`, `_timeout_total`) | active = max (5), pending > 0 | connection pool too small / DB slow |
| JVM heap and GC (`jvm_memory_used_bytes`, `jvm_gc_pause_seconds`) | heap not returning after GC in soak | memory leak or too small heap |
| Postgres: CPU, active sessions, lock waits, WAL rate, `pg_stat_statements` top queries, cache hit ratio | seq scans, lock waits | DB (query, index, IO) |
| k6: `dropped_iterations`, VU count | rises while server is idle | generator limit, not UAM |
| Auth counters (`uam_auth_login_attempts_total`, `uam_auth_lockouts_total`, `uam_jwt_validation_failures_total`) | unexpected failures or lockouts | test data or token problems |

Record the saturated resource in `results-template.md`; the breakpoint number is only meaningful with it.

## 6. After the run

1. Fill a copy of `results-template.md` per run (`results/` is git-ignored; commit the filled templates to `docs/capacity/` if wanted).
2. Put the mixed-mode breakpoint (minus your safety margin) into `docs/capacity/scenario.json` as
   `instance.sustainable_rps_per_instance`, and the measured `login_cpu_ms`, `other_cpu_ms` and Hikari hold times
   into `cost_per_request`, then change their `basis` from `PLACEHOLDER` to `MEASUREMENT` and `python capacity_model.py scenario.json`.
3. Run `seed/post_seed.sql`, then the EXPLAIN checklist in `docs/capacity/query-review.md` on the loaded database.

## What was and was not verified

Verified here: all scripts load and run their iteration code against mock k6 modules in Node (imports/exports,
config names, thresholds, request paths, the breakpoint summary logic); the seed SQL ran on a real embedded
PostgreSQL with the psql-only parts emulated, rows were read back through UAM's own JPA entity, and the hash round
trip uses Spring's `BCryptPasswordEncoder`. **Not** verified: any k6 execution (k6 is not installed here), `psql`
itself (variables, `\if`, `\gexec` were reviewed, not run), `export_credentials.sh` end to end (its query was run),
anything on a full-size database, and any throughput or latency figure.
