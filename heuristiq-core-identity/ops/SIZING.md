# UAM sizing guide

Status: guidance to be validated by the load test and the capacity model (`ops/loadtest`, `docs/capacity`). Nothing here is a measured
number. The settings in `ops/k8s` are conservative starting points, and the code defaults were not changed.

Target scenario given by the product owner: about 10 million registered users, 30-40% active per day, 10-20% of users active in the
busiest window, PostgreSQL in production.

## 1. What to measure, and where

| Question | Dashboard / metric |
|---|---|
| Requests per second per instance | *Capacity & Auth*: requests/s and `http_server_requests_seconds_count` |
| CPU cores used, CPU cost per request | `process_cpu_usage * system_cpu_count`, CPU-seconds per request panel |
| Headroom against the 65% scaling target | *Capacity & Auth > Headroom to the 65% target* |
| Heap needed | live set after GC (`jvm_gc_live_data_size_bytes`), not heap used |
| Container memory vs limit, throttling | cAdvisor `container_memory_working_set_bytes`, `container_cpu_cfs_throttled_periods_total` |
| Is the database pool the limit | `hikaricp_connections_pending`, acquire time, usage time |
| Login cost and speed | `uam_auth_login_duration_seconds_*` by result |

The dashboards and alerts live in the Observability pipeline (`jenkins-pipeline/realboom/Observability`): dashboard 18 and the alerts in
`prometheus/alerts-extra.yml`.

## 2. Database connections: pool size and the Postgres limit

Thread model: UAM serves requests on virtual threads (`spring.threads.virtual.enabled=true`), so there is no request thread pool to
exhaust. The concurrency that actually reaches the database is limited by the Hikari pool; the other fixed limit is the password hashing
pool (CPU bound, sized from `availableProcessors`, queue 64, then HTTP 503). Therefore size the pool and the CPU, not threads.

Rule for the fleet:

```
instances (max replicas) x maximumPoolSize  <=  max_connections - headroom
```

Headroom covers superuser/maintenance connections, migrations (Flyway runs at start-up), other services sharing the server, replicas and
monitoring (postgres-exporter). Example of the arithmetic only: with `max_connections = 200`, 40 reserved for others and tools, and 12
replicas, the pool cannot exceed `(200 - 40) / 12 = 13` per instance. Use your real values.

Starting point for one instance's pool (the classic Hikari/PostgreSQL formula): `connections = (core_count * 2) + effective_spindle_count`
where `core_count` is the database server's cores, then split across instances. It is a first guess: a pool that is too large makes
the database slower. Tune with the load test, watching in this order: `hikaricp_connections_pending` (should stay 0), acquire time (should
stay in single-digit milliseconds), usage time (slow queries), utilisation. Little's law helps: connections needed is about requests/s
that touch the database x average usage seconds (dashboard panel "Connections busy on average").

The code default stays `UAM_POSTGRES_MAX_POOL_SIZE=5` (`application-postgres.properties`); `deployment.yaml` restates it as an
environment variable so it can be changed per environment without a rebuild. If instances x pool approaches `max_connections`, put
**PgBouncer in transaction mode** in between (see the checklist; it needs `prepareThreshold=0` or a driver and PgBouncer that support
protocol-level prepared statements, and it breaks session features such as advisory locks and `SET`).

## 3. Memory and JVM flags

The Dockerfile default (`SerialGC`, `MaxRAMPercentage=45`, container 640m, `MaxMetaspaceSize=160m`, `ReservedCodeCacheSize=64m`,
`MaxDirectMemorySize=64m`, `Xss512k`) is a development/CI default for a very small container. With 640m it leaves about 290 MB heap and a
lot of headroom for non-heap memory, so it was chosen to avoid OOM kills, not for throughput. It is not a production profile.

What to test for production, one change at a time, with the same load profile:

1. Container memory 1Gi and 2Gi (`deployment.yaml` uses 1Gi as the conservative start; request equal to limit).
2. `-XX:MaxRAMPercentage` between 60 and 75. The rest must cover metaspace (cap with `MaxMetaspaceSize`), code cache, direct buffers
   (`MaxDirectMemorySize`, used by Netty/WebClient), thread stacks and native memory. Check the panel "Memory outside the heap and JVM
   pools" to see what the container really needs beyond the heap before raising the percentage.
3. SerialGC vs G1. Serial is cheap and fine for one or two cores and small heaps, but its pauses grow with the heap; G1 costs more CPU and
   memory overhead but has shorter pauses on larger heaps and more cores. Decide on GC pause ratio (alert at 10%) and login p95/p99.
4. Heap size from the **live set after GC** (the panel and the leak alert use `jvm_gc_live_data_size_bytes`): a common starting point is
   a heap about 2-3 times the live set, then confirm GC time and allocation rate under peak.
5. A soak run (hours) to look at the live-set trend: a rising line is a leak.

`ExitOnOutOfMemoryError` stays, so an OOM becomes a clean restart rather than a half-working pod.

## 4. CPU

Password hashing (bcrypt strength 11, about 200 ms per login on the reference hardware used earlier) makes logins CPU bound, while token
refresh and permission checks are mostly database bound. Cores per pod therefore decide login throughput; replicas decide the total.
`deployment.yaml` keeps request 2 / limit 4 cores and the HPA scales at 65% of the request. Validate: logins per second per core (the load
test), CPU-seconds per request, and CPU throttling (alert at 25% of periods). Do not lower the bcrypt strength to gain capacity without the
security owner.

## 5. What the Kubernetes files assume (all placeholders to validate)

* `deployment.yaml`: probes on `/api/v1/uam/actuator/health/{liveness,readiness}` with a startup probe (up to 3 minutes for Flyway and the
  default-organization setup); `terminationGracePeriodSeconds: 50` > `preStop` 10s + the 30 s graceful shutdown phase; memory
  request = limit = 1Gi; CPU 2 / 4; pool size and min idle as environment variables.
* `hpa.yaml`: CPU target 65%, min 4 / max 12 replicas as placeholders; an optional requests-per-second-per-pod metric left commented until
  the load test gives the sustainable value. Max replicas is bounded by the database connection budget above.

## 6. PostgreSQL checklist (review with the DBA, validate under load)

- [ ] `max_connections` against section 2 (replicas x pool + headroom); consider PgBouncer (transaction mode) before raising it.
- [ ] `shared_buffers` (commonly about 25% of RAM) and `effective_cache_size` (about 50-75% of RAM); check the cache hit ratio panel (should stay above 99%).
- [ ] `work_mem` per sort/hash node, multiplied by concurrent queries: raise carefully; watch temp-file writes on the PostgreSQL dashboard.
- [ ] `maintenance_work_mem`, `max_wal_size` and `checkpoint_timeout` for write bursts (login creates sessions, refresh tokens, audit rows).
- [ ] Autovacuum for the hot, high-churn tables: refresh tokens, sessions, login-attempt/lockout state, OTP challenges and the auth audit
  tables. Consider lower `autovacuum_vacuum_scale_factor` / `autovacuum_vacuum_threshold` per table, and retention (deleting or partitioning
  expired tokens and old audit rows) so the tables do not grow without bound at 10 million users.
- [ ] Indexes on the login lookups (user name, e-mail and phone are all tried per login, case-insensitive for the first two), on refresh
  token hash, and on the audit filter columns. Migration `V15__login_hot_path_indexes.sql` is part of the capacity work; confirm with
  `EXPLAIN (ANALYZE, BUFFERS)` on production-sized data that the planner uses them.
- [ ] Replicas: decide whether read-only endpoints may use a replica (not implemented in UAM today).
- [ ] Monitoring: postgres-exporter panels for connections, locks, deadlocks, long transactions.

## 7. Left for the load test and capacity model

Everything numeric: sustainable logins/s and requests/s per pod, CPU per request, the replica counts (min/max), the pool size per
instance, the container memory (1Gi or 2Gi), `MaxRAMPercentage`, the GC choice, the HPA custom-metric target, and the alert thresholds
(all set as sensible defaults, not derived from measurements).
