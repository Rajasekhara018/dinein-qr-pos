-- V10: payment-gateway traffic log and audit outbox.
--  * payment_gateway_calls: one row per outbound gateway call (create order, refund, status check).
--    Metadata only -- never request/response bodies, credentials or card data.
--  * payment_audit_outbox: PAYMENT_GATEWAY audit events waiting for HTTP delivery to Rider_AuditService
--    (event_id = call id / webhook row id, so redelivery is idempotent there).
--  * payment_webhook_events: every received webhook delivery is now logged (provider, signature validity,
--    outcome). DUPLICATE/REJECTED rows leave provider_event_id NULL so the unique dedupe key is untouched.
-- No foreign keys on purpose: logging must never be able to block or roll back a payment.
CREATE TABLE payment_gateway_calls (
    id              VARCHAR(64)  PRIMARY KEY,
    gateway         VARCHAR(32)  NOT NULL,
    operation       VARCHAR(32)  NOT NULL,
    payment_id      VARCHAR(255),
    reference       VARCHAR(128),
    gateway_ref     VARCHAR(255),
    http_status     INTEGER,
    error_class     VARCHAR(128),
    outcome         VARCHAR(16)  NOT NULL,
    failure_reason  VARCHAR(200),
    latency_ms      BIGINT       NOT NULL,
    attempt         INTEGER      NOT NULL,
    correlation_id  VARCHAR(128),
    created_at      TIMESTAMP    NOT NULL
);
CREATE INDEX idx_payment_gateway_calls_payment ON payment_gateway_calls(payment_id, created_at);
CREATE INDEX idx_payment_gateway_calls_created ON payment_gateway_calls(created_at);

CREATE TABLE payment_audit_outbox (
    id               BIGSERIAL PRIMARY KEY,
    event_id         VARCHAR(64)   NOT NULL UNIQUE,
    payload          VARCHAR(8000) NOT NULL,
    created_at       TIMESTAMP     NOT NULL,
    next_attempt_at  TIMESTAMP     NOT NULL,
    published_at     TIMESTAMP,
    attempts         INTEGER       NOT NULL DEFAULT 0
);
CREATE INDEX idx_payment_audit_outbox_pending ON payment_audit_outbox(next_attempt_at, id) WHERE published_at IS NULL;
CREATE INDEX idx_payment_audit_outbox_published ON payment_audit_outbox(published_at) WHERE published_at IS NOT NULL;

ALTER TABLE payment_webhook_events ALTER COLUMN provider_event_id DROP NOT NULL;
ALTER TABLE payment_webhook_events ADD COLUMN provider         VARCHAR(32);
ALTER TABLE payment_webhook_events ADD COLUMN signature_valid  BOOLEAN;
ALTER TABLE payment_webhook_events ADD COLUMN outcome          VARCHAR(16);
ALTER TABLE payment_webhook_events ADD COLUMN claimed_event_id VARCHAR(255);
CREATE INDEX idx_payment_webhook_events_payment ON payment_webhook_events(payment_id);
