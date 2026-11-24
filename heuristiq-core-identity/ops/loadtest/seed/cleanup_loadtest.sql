-- TEST ONLY. Removes everything the seed and the load tests created for the test organization.
--   PGOPTIONS="-c uam.loadtest_seed=allow" psql "$UAM_TEST_DB_URL" -X -v tenant_id=loadtest -f cleanup_loadtest.sql
--
-- At 10M users this is slow (it deletes tens of millions of rows and some of the child tables have no index on
-- user_id, see query-review.md). The honest cleanup of a large load-test database is to drop the whole database
-- (DROP DATABASE uam_loadtest) and recreate it. Use this script only for a small seed or when the database also
-- holds data you must keep.
--
-- It touches only rows of users whose id starts with 'lt-u-' and the tenant row whose id starts with 'loadtest'.

\set ON_ERROR_STOP on
\if :{?tenant_id}
\else
  \set tenant_id loadtest
\endif

DO $gate$
BEGIN
  IF coalesce(current_setting('uam.loadtest_seed', true), '') <> 'allow' THEN
    RAISE EXCEPTION 'Refusing to run: set PGOPTIONS="-c uam.loadtest_seed=allow" to confirm this is a TEST database.';
  END IF;
  IF current_database() ~* '(prod|production|live)' THEN
    RAISE EXCEPTION 'Refusing to run: database name "%" looks like production.', current_database();
  END IF;
END
$gate$;

select (:'tenant_id' ~ '^loadtest[a-z0-9_-]{0,40}$') as tenant_ok \gset
\if :tenant_ok
\else
  \echo 'ERROR: tenant_id must start with "loadtest".'
  \quit
\endif

delete from refresh_tokens where principal_id like 'lt-u-%';
delete from user_sessions where user_id like 'lt-u-%';
delete from user_devices where user_id like 'lt-u-%';
delete from user_login_security where user_id like 'lt-u-%';
delete from user_mfa where user_id like 'lt-u-%';
delete from password_history where user_id like 'lt-u-%';
delete from audit_logs where user_id like 'lt-u-%';
delete from uam_auth_audit_events where user_id like 'lt-u-%' or username like 'lt\_user\_%';
delete from uam_login_attempts where username like 'lt\_user\_%';
delete from user_credentials where user_id like 'lt-u-%';
delete from uam_user_roles where user_id like 'lt-u-%';
delete from tenant_memberships where tenant_id = :'tenant_id';
delete from uam_users where tenant_id = :'tenant_id' and id like 'lt-u-%';
delete from tenants where id = :'tenant_id' and system_tenant = false;

\echo 'Cleanup done. Run post_seed.sql (VACUUM ANALYZE) next.'
