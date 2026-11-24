-- Optimistic policy edits, including platform/global scopes whose nullable columns otherwise permit duplicates.
-- AuditInfo already has a legacy version column; policy locking needs a distinct JPA column.
ALTER TABLE security_policies ADD COLUMN IF NOT EXISTS policy_revision BIGINT NOT NULL DEFAULT 0;
-- Existing installations must resolve duplicate scope records before enabling this invariant.
CREATE UNIQUE INDEX IF NOT EXISTS uk_security_policy_scope
    ON security_policies (COALESCE(tenant_id, ''), COALESCE(application_id, ''));
