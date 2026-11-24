-- Lifecycle of each payment: one row per status change (INITIATED -> CAPTURED -> SETTLED ...).
-- Written by PaymentLifecycleListener whenever a payments row is inserted or its status changes.
-- No foreign key on purpose: recording history must never be able to block or roll back a payment.
CREATE TABLE payment_status_transitions (
    id BIGSERIAL PRIMARY KEY,
    payment_id VARCHAR(255) NOT NULL,
    from_status VARCHAR(32),
    to_status VARCHAR(32) NOT NULL,
    source VARCHAR(64),
    reason VARCHAR(500),
    transitioned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_payment_status_transitions_payment ON payment_status_transitions(payment_id, id);
