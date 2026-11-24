-- V26: settle or cancel the Wallet sale of a gateway payment (docs/FRANCHISE_PLAN.md step 8, decision D7).
-- A ride, matrimony or food sale paid through the gateway is booked in Rider_WalletService as PENDING; its earnings wait until
-- this service tells the wallet the money arrived (POST /internal/sales/{eventId}/settle) or never will (.../cancel).
--
-- One row per (sale event, action), written in the same transaction as the payment's status change, so the request survives
-- a crash or a wallet outage. A ShedLock-guarded job sends due rows with backoff; the wallet call is idempotent on the
-- idempotency key (sale-settle:<eventId> / sale-cancel:<eventId>), so a repeat after an unknown outcome is harmless.
CREATE TABLE sale_settlement_requests (
    id              BIGSERIAL PRIMARY KEY,
    idempotency_key VARCHAR(300) NOT NULL UNIQUE,
    event_id        VARCHAR(255) NOT NULL,
    action          VARCHAR(16)  NOT NULL,
    payment_id      VARCHAR(255) NOT NULL,
    tenant_id       VARCHAR(255),
    reason          VARCHAR(500),
    status          VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    attempts        INTEGER      NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_error      VARCHAR(500),
    outcome         VARCHAR(64),
    wallet_journal_id VARCHAR(255),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at    TIMESTAMPTZ,
    CONSTRAINT ck_sale_settlement_requests_action CHECK (action IN ('SETTLE', 'CANCEL')),
    CONSTRAINT ck_sale_settlement_requests_status CHECK (status IN ('PENDING', 'DONE', 'SUPERSEDED', 'FAILED'))
);

-- The job reads the oldest due rows still to send.
CREATE INDEX ix_sale_settlement_requests_due ON sale_settlement_requests (next_attempt_at, id) WHERE status = 'PENDING';
-- Support and the cancel guard look rows up by sale.
CREATE INDEX ix_sale_settlement_requests_event ON sale_settlement_requests (event_id);
