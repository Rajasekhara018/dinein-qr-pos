-- Status poll (findPollCandidatesAfter: status IN (...) AND gateway IN (...), keyset on the payment id).
-- Built CONCURRENTLY so writes carry on during the build. Flyway (10.x, flyway-database-postgresql) runs a script
-- holding CREATE INDEX CONCURRENTLY outside a transaction, so this file holds this one statement only: do not add
-- others. Needs spring.flyway.postgresql.transactional-lock=false (application.properties). A failed build leaves an
-- INVALID index of this name, which IF NOT EXISTS would accept: drop it before migrating again.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payments_status_gateway ON payments (status, gateway);
