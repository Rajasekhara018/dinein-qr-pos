-- An organization's user list, newest first.
-- Built CONCURRENTLY so writes carry on during the build. Flyway (flyway-database-postgresql) runs a script holding
-- CREATE INDEX CONCURRENTLY outside a transaction, so this file holds this one statement only: do not add others.
-- Needs spring.flyway.postgresql.transactional-lock=false (application-postgres.properties): with the transaction-scoped Flyway lock the build
-- waits forever on Flyway's own open transaction. A failed build leaves an INVALID index of this name, which
-- IF NOT EXISTS would accept: drop it before migrating again.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_uam_users_tenant_created ON uam_users (tenant_id, created_at DESC);
