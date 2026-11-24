-- An organization can own its own template for an event and channel, used instead of the shared one when that
-- organization sends. NULL tenant_id = a shared template (every organization). Uniqueness is per organization, and
-- the shared templates stay unique on their own (a plain UNIQUE would let two NULLs through).

ALTER TABLE nms_templates ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(255);

DROP INDEX IF EXISTS uk_nms_template_event_channel;

CREATE UNIQUE INDEX IF NOT EXISTS uk_nms_template_shared ON nms_templates(event, channel) WHERE tenant_id IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_nms_template_org ON nms_templates(event, channel, tenant_id) WHERE tenant_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_nms_template_tenant_id ON nms_templates(tenant_id);
