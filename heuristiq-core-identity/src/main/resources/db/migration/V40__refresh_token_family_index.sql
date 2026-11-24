-- Active tokens of one rotation family (reuse detection revokes them all): findByFamilyIdAndRevokedFalse.
-- Built CONCURRENTLY so writes carry on during the build. Flyway (flyway-database-postgresql) runs a script holding
-- CREATE INDEX CONCURRENTLY outside a transaction, so this file holds this one statement only: do not add others.
-- Needs spring.flyway.postgresql.transactional-lock=false (application-postgres.properties). A failed build leaves an
-- INVALID index of this name, which IF NOT EXISTS would accept: drop it before migrating again.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_refresh_tokens_family ON refresh_tokens (family_id, revoked);
