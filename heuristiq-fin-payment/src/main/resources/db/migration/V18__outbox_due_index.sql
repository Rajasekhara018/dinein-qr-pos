-- The drain job takes unpublished rows in id order. idx_outbox_pending (aggregate_key, id) serves the per-key ordering
-- check but not that order, so without this index the scan walks every row ever published.
--
-- Built CONCURRENTLY so writes to the table carry on during the build. Flyway (10.x, flyway-database-postgresql)
-- recognises CREATE INDEX CONCURRENTLY and runs this script outside a transaction, which is why it holds this one
-- statement only: do not add other statements to this file. It needs spring.flyway.postgresql.transactional-lock=false
-- (set in application.properties): with the default transaction-scoped Flyway lock the build waits forever on
-- Flyway's own open transaction. If a build fails it leaves an INVALID index of this name, which IF NOT EXISTS would
-- accept: drop it before migrating again.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_outbox_due ON integration_outbox (id) WHERE published_at IS NULL;
