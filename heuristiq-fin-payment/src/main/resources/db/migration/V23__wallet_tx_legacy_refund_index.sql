-- Refund cap check (sumLegacyRefunds: source = 'REFUND' AND idempotency_key IS NULL AND reference_id = ?). Partial:
-- only legacy refund rows, so the index stays small.
-- Built CONCURRENTLY so writes carry on during the build. Flyway (10.x, flyway-database-postgresql) runs a script
-- holding CREATE INDEX CONCURRENTLY outside a transaction, so this file holds this one statement only: do not add
-- others. Needs spring.flyway.postgresql.transactional-lock=false (application.properties). A failed build leaves an
-- INVALID index of this name, which IF NOT EXISTS would accept: drop it before migrating again.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_wtx_legacy_refund ON wallet_transactions (reference_id) WHERE source = 'REFUND' AND idempotency_key IS NULL;
