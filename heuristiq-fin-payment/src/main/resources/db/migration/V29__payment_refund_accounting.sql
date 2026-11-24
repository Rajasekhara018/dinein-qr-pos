CREATE TABLE payment_refund_accounting (
    refund_event_id VARCHAR(200) PRIMARY KEY,
    tenant_id VARCHAR(64),
    event_id VARCHAR(200) NOT NULL,
    amount NUMERIC(14,2) NOT NULL CHECK(amount > 0),
    amount_currency VARCHAR(3) NOT NULL,
    credit_already_posted BOOLEAN NOT NULL,
    state VARCHAR(16) NOT NULL DEFAULT 'PENDING' CHECK(state IN ('PENDING','DONE')),
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    locked_until TIMESTAMPTZ,
    last_error VARCHAR(500)
);
CREATE INDEX idx_payment_refund_accounting_due ON payment_refund_accounting(next_attempt_at) WHERE state='PENDING';
