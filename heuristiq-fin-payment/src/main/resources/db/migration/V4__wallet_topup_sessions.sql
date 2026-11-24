-- V4: real, gateway-verified wallet top-ups. WalletController's POST /{customerId}/topup used to
-- be reachable directly and just trusted the caller's amount with no payment verification at all
-- — this table backs a proper checkout-session + webhook/poll-confirmed flow instead (see
-- WalletTopupService), mirroring the payments table's own INITIATED -> CAPTURED state machine but
-- kept separate from it (wallet_topup_sessions has no ride_id — the payments table's ride_id is
-- NOT NULL, so a rideless wallet top-up can't live there without loosening a constraint every
-- ride-payment code path currently relies on).
CREATE TABLE wallet_topup_sessions (
    id                  VARCHAR(255) PRIMARY KEY,
    customer_id         VARCHAR(255) NOT NULL,
    amount              DOUBLE PRECISION NOT NULL,
    currency            VARCHAR(8) NOT NULL DEFAULT 'INR',
    gateway             VARCHAR(32) NOT NULL,
    status              VARCHAR(32) NOT NULL,
    provider_order_id   VARCHAR(255),
    provider_payment_id VARCHAR(255),
    idempotency_key     VARCHAR(255) NOT NULL UNIQUE,
    failure_reason      VARCHAR(500),
    created_at          TIMESTAMP NOT NULL,
    updated_at          TIMESTAMP NOT NULL
);

CREATE INDEX idx_wallet_topup_sessions_customer_id ON wallet_topup_sessions(customer_id);
CREATE INDEX idx_wallet_topup_sessions_provider_order_id ON wallet_topup_sessions(provider_order_id);
