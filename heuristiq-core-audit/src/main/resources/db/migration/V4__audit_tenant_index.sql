-- Organization (tenant) separation. audit_events.tenant_id exists since V1 (nullable: a record with no organization is
-- a legacy record visible to every reader); this makes sure the column exists and indexes the per-organization reads
-- (list, facets, export).
ALTER TABLE audit_events ADD COLUMN IF NOT EXISTS tenant_id varchar(64);
CREATE INDEX IF NOT EXISTS idx_audit_tenant_time ON audit_events (tenant_id, occurred_at DESC);
