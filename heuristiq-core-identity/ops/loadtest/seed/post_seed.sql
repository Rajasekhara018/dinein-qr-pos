-- TEST ONLY. Run after seeding and again after every load-test session.
--   psql "$UAM_TEST_DB_URL" -X -f post_seed.sql
--
-- A bulk load leaves no planner statistics and no visibility map, so the first EXPLAINs would be misleading and
-- index-only scans would not happen. VACUUM cannot run inside a transaction block, so these are separate statements
-- (psql runs each file statement on its own unless you wrap it in BEGIN).
--
-- After a LOAD TEST the same commands also remove the dead rows the test made (refresh-token rotation and the
-- per-login updates produce many) and refresh statistics. Compare n_dead_tup before and after to see bloat.

\echo 'Before: dead tuples on the hot tables'
select relname, n_live_tup, n_dead_tup, last_autovacuum, last_autoanalyze
from pg_stat_user_tables
where relname in ('uam_users','user_credentials','tenant_memberships','user_login_security','user_devices',
                  'user_sessions','refresh_tokens','audit_logs','uam_auth_audit_events','uam_login_attempts')
order by n_dead_tup desc;

vacuum (analyze, verbose) uam_users;
vacuum (analyze) uam_user_roles;
vacuum (analyze) user_credentials;
vacuum (analyze) tenant_memberships;
vacuum (analyze) user_login_security;
vacuum (analyze) user_devices;
vacuum (analyze) user_sessions;
vacuum (analyze) refresh_tokens;
vacuum (analyze) audit_logs;
vacuum (analyze) uam_auth_audit_events;
vacuum (analyze) uam_login_attempts;
vacuum (analyze) application_access;
vacuum (analyze) tenants;

\echo 'Sizes (heap + indexes), largest first - paste into capacity model storage assumptions'
select c.relname as table_name,
       pg_size_pretty(pg_total_relation_size(c.oid)) as total,
       pg_size_pretty(pg_relation_size(c.oid)) as heap,
       pg_size_pretty(pg_indexes_size(c.oid)) as indexes,
       c.reltuples::bigint as approx_rows,
       case when c.reltuples > 0 then (pg_total_relation_size(c.oid) / c.reltuples)::int end as bytes_per_row_total
from pg_class c
join pg_namespace n on n.oid = c.relnamespace
where n.nspname = 'public' and c.relkind = 'r'
order by pg_total_relation_size(c.oid) desc
limit 20;
