-- Organization (tenant) separation. Every record that belongs to an organization stores its id; NULL means the
-- record was stored before organizations existed and stays visible to every organization until backfilled.
-- Templates and event definitions are global and are not touched.

ALTER TABLE nms_messages ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(255);
ALTER TABLE nms_logs ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(255);
ALTER TABLE nms_delivery_attempts ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(255);
ALTER TABLE nms_in_app_notifications ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(255);
ALTER TABLE nms_broadcasts ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(255);
ALTER TABLE nms_device_tokens ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_nms_message_tenant_id ON nms_messages(tenant_id);
CREATE INDEX IF NOT EXISTS idx_nms_log_tenant_id ON nms_logs(tenant_id);
CREATE INDEX IF NOT EXISTS idx_nms_attempt_tenant_id ON nms_delivery_attempts(tenant_id);
CREATE INDEX IF NOT EXISTS idx_nms_in_app_tenant_user ON nms_in_app_notifications(tenant_id, user_id);
CREATE INDEX IF NOT EXISTS idx_nms_broadcast_tenant_id ON nms_broadcasts(tenant_id);
CREATE INDEX IF NOT EXISTS idx_nms_device_token_tenant_id ON nms_device_tokens(tenant_id);
