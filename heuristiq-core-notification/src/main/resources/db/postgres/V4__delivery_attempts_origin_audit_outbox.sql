-- Communication log, origin to destination.
--  1. Origin of a request (calling service, template/event code, triggering user, entity) on the
--     message and on the per-recipient log row. All columns are nullable: existing producers that
--     send no origin keep working and old rows stay valid.
--  2. One row per provider attempt (retries included), separate from the per-recipient summary in nms_logs.
--  3. Outbox of COMMUNICATION audit events waiting to be pushed to Rider_AuditService.
-- DeliveryStatus gains QUEUED, DELIVERED, BOUNCED and READ; the status columns are plain VARCHAR, so no
-- column change is needed for them.

ALTER TABLE nms_messages ADD COLUMN IF NOT EXISTS source_service VARCHAR(255);
ALTER TABLE nms_messages ADD COLUMN IF NOT EXISTS event_code VARCHAR(255);
ALTER TABLE nms_messages ADD COLUMN IF NOT EXISTS triggering_user_id VARCHAR(255);
ALTER TABLE nms_messages ADD COLUMN IF NOT EXISTS entity_type VARCHAR(255);
ALTER TABLE nms_messages ADD COLUMN IF NOT EXISTS entity_id VARCHAR(255);

ALTER TABLE nms_logs ADD COLUMN IF NOT EXISTS source_service VARCHAR(255);
ALTER TABLE nms_logs ADD COLUMN IF NOT EXISTS event_code VARCHAR(255);
ALTER TABLE nms_logs ADD COLUMN IF NOT EXISTS triggering_user_id VARCHAR(255);
ALTER TABLE nms_logs ADD COLUMN IF NOT EXISTS entity_type VARCHAR(255);
ALTER TABLE nms_logs ADD COLUMN IF NOT EXISTS entity_id VARCHAR(255);

CREATE INDEX idx_nms_log_source_service ON nms_logs(source_service);

CREATE TABLE nms_delivery_attempts (
    id                  VARCHAR(255) PRIMARY KEY,
    log_id              VARCHAR(255),
    message_id          VARCHAR(255),
    correlation_id      VARCHAR(255),
    event               VARCHAR(255),
    source_service      VARCHAR(255),
    event_code          VARCHAR(255),
    triggering_user_id  VARCHAR(255),
    entity_type         VARCHAR(255),
    entity_id           VARCHAR(255),
    channel             VARCHAR(255),
    provider_name       VARCHAR(255),
    provider_message_id VARCHAR(255),
    attempt_number      INTEGER NOT NULL,
    status              VARCHAR(255),
    provider_status     VARCHAR(255),
    error_code          VARCHAR(255),
    error_message       VARCHAR(2000),
    recipient           VARCHAR(255),
    latency_ms          BIGINT,
    started_at          TIMESTAMP,
    completed_at        TIMESTAMP,
    status_updated_at   TIMESTAMP,
    version             BIGINT,
    CONSTRAINT uk_nms_attempt_log_number UNIQUE (log_id, attempt_number)
);

CREATE INDEX idx_nms_attempt_message_id ON nms_delivery_attempts(message_id);
CREATE INDEX idx_nms_attempt_correlation_id ON nms_delivery_attempts(correlation_id);
CREATE INDEX idx_nms_attempt_provider_msg_id ON nms_delivery_attempts(provider_message_id);
CREATE INDEX idx_nms_attempt_started_at ON nms_delivery_attempts(started_at);

CREATE TABLE nms_audit_outbox (
    id              VARCHAR(255) PRIMARY KEY,
    event_id        VARCHAR(255) NOT NULL,
    payload         VARCHAR(8000) NOT NULL,
    status          VARCHAR(255) NOT NULL,
    attempts        INTEGER NOT NULL,
    next_attempt_at TIMESTAMP,
    locked_until    TIMESTAMP,
    lock_owner      VARCHAR(255),
    last_error      VARCHAR(500),
    created_at      TIMESTAMP,
    sent_at         TIMESTAMP,
    expire_at       TIMESTAMP,
    CONSTRAINT uk_nms_audit_outbox_event_id UNIQUE (event_id)
);

CREATE INDEX idx_nms_audit_outbox_due ON nms_audit_outbox(status, next_attempt_at);
CREATE INDEX idx_nms_audit_outbox_expire ON nms_audit_outbox(expire_at);
