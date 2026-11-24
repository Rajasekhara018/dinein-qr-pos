-- =====================================================================================================
-- TEST ONLY. NEVER RUN THIS AGAINST A DATABASE THAT HOLDS REAL USERS.
-- Seeds N synthetic users for Rider_UAM load tests (PostgreSQL). Not a Flyway migration; do not copy it into
-- src/main/resources/db/migration.
--
-- What it creates, all in ONE test organization (tenants.id = :tenant_id, default 'loadtest'):
--   tenants              1 row (the test organization, ACTIVE)
--   uam_users            N rows   id 'lt-u-00000001'.., user_name 'lt_user_00000001'.., email 'lt00000001@loadtest.invalid',
--                                 phone_e164 '+919000000001'.. (unique, fake: 9000000000 + n), status ACTIVE
--   uam_user_roles       N rows   one role (default CUSTOMER)
--   user_credentials     N rows   provider LOCAL, active, password_hash = the bcrypt hash you pass in (see README)
--   tenant_memberships   N rows   ACTIVE membership of each user in the test organization (organization rules in CLAUDE.md)
-- Not created (UAM creates them on first login, which is part of what is being measured): user_login_security,
-- user_devices, user_sessions, refresh_tokens, audit rows.
--
-- Safety, all of which must hold or the script stops before writing anything:
--   * the session setting uam.loadtest_seed must be 'allow'      (PGOPTIONS="-c uam.loadtest_seed=allow")
--   * the database name must not look like production             (prod, production, live)
--   * the tenant id must start with 'loadtest'                    (so it can never be 'default' or a real organization)
--   * pwhash must be a bcrypt hash                                 (so login can work)
--   * the UAM schema must already exist (start UAM once, or run Flyway, first)
-- Every row it writes carries the test tenant id or an 'lt-' id prefix, and every insert is ON CONFLICT DO NOTHING,
-- so it is idempotent (re-run to top up) and cannot overwrite an existing row.
--
-- Usage (see ../README.md for the full procedure):
--   PGOPTIONS="-c uam.loadtest_seed=allow" psql "$UAM_TEST_DB_URL" -X \
--       -v pwhash="$LOADTEST_PWHASH" -v n_users=10000000 -v batch=100000 -f seed_loadtest.sql
--
-- Time and disk: no figures are quoted here because they depend on your hardware. Measure a small run first
-- (-v n_users=200000 with \timing on), then   total time ~= measured seconds x (N / 200000)
-- and   disk ~= measured pg_total_relation_size sum x (N / 200000)   (ANALYZE first; see README "Estimating").
-- =====================================================================================================

\set ON_ERROR_STOP on
\set QUIET on

\if :{?n_users}
\else
  \set n_users 10000000
\endif
\if :{?batch}
\else
  \set batch 100000
\endif
\if :{?tenant_id}
\else
  \set tenant_id loadtest
\endif
\if :{?role_code}
\else
  \set role_code CUSTOMER
\endif
\if :{?pwhash}
\else
  \echo 'ERROR: pass the bcrypt hash of the test password: -v pwhash=... (see ../README.md, "Password hash"). Nothing was written.'
  \quit
\endif

-- ---- safety gate (nothing has been written yet) ----------------------------------------------------------
DO $gate$
BEGIN
  IF coalesce(current_setting('uam.loadtest_seed', true), '') <> 'allow' THEN
    RAISE EXCEPTION 'Refusing to run: set PGOPTIONS="-c uam.loadtest_seed=allow" to confirm this is a TEST database.';
  END IF;
  IF current_database() ~* '(prod|production|live)' THEN
    RAISE EXCEPTION 'Refusing to run: database name "%" looks like production.', current_database();
  END IF;
  IF to_regclass('public.uam_users') IS NULL OR to_regclass('public.tenants') IS NULL
     OR to_regclass('public.user_credentials') IS NULL OR to_regclass('public.tenant_memberships') IS NULL THEN
    RAISE EXCEPTION 'Refusing to run: the UAM schema is not in this database (start UAM once, or run Flyway, first).';
  END IF;
END
$gate$;

select (:'tenant_id' ~ '^loadtest[a-z0-9_-]{0,40}$') as tenant_ok,
       (:'pwhash' ~ '^\$2[aby]\$[0-9]{2}\$[./A-Za-z0-9]{53}$') as hash_ok,
       (:n_users::bigint between 1 and 99999999) as count_ok,
       (:batch::bigint between 1000 and 1000000) as batch_ok,
       substring(:'pwhash' from '^\$2[aby]\$([0-9]{2})\$') as bcrypt_cost \gset

\if :tenant_ok
\else
  \echo 'ERROR: tenant_id must start with "loadtest" (lower case letters, digits, - and _ afterwards).'
  \quit
\endif
\if :hash_ok
\else
  \echo 'ERROR: pwhash is not a bcrypt hash ($2a$/$2b$/$2y$ + cost + 53 characters).'
  \quit
\endif
\if :count_ok
\else
  \echo 'ERROR: n_users must be between 1 and 99999999 (the id and user name use 8 digits).'
  \quit
\endif
\if :batch_ok
\else
  \echo 'ERROR: batch must be between 1000 and 1000000.'
  \quit
\endif

\echo 'Seeding' :n_users 'users into tenant' :tenant_id 'in database' :DBNAME ', role' :role_code ', bcrypt cost' :bcrypt_cost ', batches of' :batch
\echo 'Note: UAM verifies at the cost stored IN the hash. Production uses cost 11 (railcab.uam.auth.password.bcrypt-strength); use the same for a realistic CPU cost.'
\set QUIET off
\timing on

-- ---- the test organization --------------------------------------------------------------------------------
insert into tenants (id, code, name, type, status, active, system_tenant, version, created_at)
select :'tenant_id', upper(:'tenant_id'), 'Load test organization (TEST ONLY)', 'CUSTOMER', 'ACTIVE', true, false, 0, now()
where not exists (select 1 from tenants where id = :'tenant_id' or code = upper(:'tenant_id'));

-- ---- users, in batches: \gexec runs each generated statement on its own, so each batch is its own transaction ----
-- A batch is four inserts sent as one multi-statement string (atomic per batch). Ranges already present are skipped
-- row by row by ON CONFLICT DO NOTHING, which makes a re-run top up instead of failing.
select format($f$
  insert into uam_users (id, tenant_id, user_name, email, display_name, first_name, last_name,
                         country_code, number, phone_e164, status, active, api_user, timezone,
                         deleted, created_at, updated_at, version)
  select 'lt-u-' || lpad(g::text, 8, '0'), %1$L,
         'lt_user_' || lpad(g::text, 8, '0'), 'lt' || lpad(g::text, 8, '0') || '@loadtest.invalid',
         'Load Test ' || g, 'Load', 'User' || g,
         '91', (9000000000 + g)::text, '+91' || (9000000000 + g)::text, 'ACTIVE', true, false, 'Asia/Kolkata',
         false, now() - ((g %% 730) * interval '1 day'), now(), 0
  from generate_series(%4$s, %5$s) as g
  on conflict do nothing;

  insert into uam_user_roles (user_id, role_code, idx)
  select 'lt-u-' || lpad(g::text, 8, '0'), %2$L, 0
  from generate_series(%4$s, %5$s) as g
  on conflict do nothing;

  insert into user_credentials (id, user_id, provider, password_hash, active, primary_credential, verified,
                                email_verified, phone_verified, force_password_change,
                                password_changed_at, created_at, updated_at, version)
  select 'lt-c-' || lpad(g::text, 8, '0'), 'lt-u-' || lpad(g::text, 8, '0'), 'LOCAL', %3$L, true, true, true,
         true, true, false, now(), now(), now(), 0
  from generate_series(%4$s, %5$s) as g
  on conflict do nothing;

  insert into tenant_memberships (id, tenant_id, user_id, status, added_by, joined_at, updated_at)
  select 'lt-m-' || lpad(g::text, 8, '0'), %1$L, 'lt-u-' || lpad(g::text, 8, '0'), 'ACTIVE', 'loadtest', now(), now()
  from generate_series(%4$s, %5$s) as g
  on conflict do nothing;
$f$, :'tenant_id', :'role_code', :'pwhash', b.lo, b.hi) as batch_sql
from (
  select s as lo, least(s + :batch::bigint - 1, :n_users::bigint) as hi
  from generate_series(1::bigint, :n_users::bigint, :batch::bigint) as s
) as b
order by b.lo
\gexec

\timing off
\echo 'Seed finished. Next: psql -f post_seed.sql (VACUUM ANALYZE), then ./export_credentials.sh'

select 'uam_users' as table_name, count(*) as test_rows from uam_users where tenant_id = :'tenant_id'
union all select 'tenant_memberships', count(*) from tenant_memberships where tenant_id = :'tenant_id';
