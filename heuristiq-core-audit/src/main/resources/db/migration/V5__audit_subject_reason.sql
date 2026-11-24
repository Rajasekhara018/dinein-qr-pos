-- Find events by what they are about and why. subject_type/subject_id identify the thing an event concerns (a DMS
-- document, an NMS message, a payment); reason_code is a stable UPPER_SNAKE code (VIRUS_DETECTED ...). All nullable:
-- events from producers that do not send them stay as they are. Indexes combine with time ordering like the
-- other filter indexes; the organization limit is applied on top of them (see idx_audit_tenant_time).
ALTER TABLE audit_events ADD COLUMN IF NOT EXISTS subject_type varchar(40);
ALTER TABLE audit_events ADD COLUMN IF NOT EXISTS subject_id   varchar(128);
ALTER TABLE audit_events ADD COLUMN IF NOT EXISTS reason_code  varchar(60);

CREATE INDEX IF NOT EXISTS idx_audit_subject_time ON audit_events (subject_type, subject_id, occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_reason_time  ON audit_events (reason_code, occurred_at DESC);
