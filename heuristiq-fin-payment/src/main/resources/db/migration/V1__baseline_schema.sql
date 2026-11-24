-- Rider Payment Service — owned tables.
--
-- wallet_transactions, driver_payments, driver_payouts, driver_safety_deposits,
-- split_fare_requests, split_fare_participants are copied VERBATIM from the
-- monolith's src/main/resources/db/migration/V1__baseline_schema.sql (Rider_BookingsAPI),
-- since this service now owns that data exclusively.
--
-- payments and payment_webhook_events are new — designed for this service per HLD
-- §17.1/§17.2/§18, since the monolith has no dedicated payment-state-machine table.

-- ── wallet_transactions ──────────────────────────────────────────────────────
CREATE TABLE wallet_transactions (
    id                  VARCHAR(255) PRIMARY KEY,
    user_id             VARCHAR(255),
    amount              DOUBLE PRECISION,
    type                VARCHAR(255),
    source              VARCHAR(255),
    razorpay_order_id   VARCHAR(255),
    razorpay_payment_id VARCHAR(255),
    reference_id        VARCHAR(255),
    status              VARCHAR(255) NOT NULL DEFAULT 'PENDING',
    description         VARCHAR(255),
    created_at          TIMESTAMP
);
CREATE INDEX idx_wallet_transactions_user_created ON wallet_transactions(user_id, created_at);
CREATE INDEX idx_wallet_transactions_razorpay_order_id ON wallet_transactions(razorpay_order_id);

-- ── driver_payouts ───────────────────────────────────────────────────────────
CREATE TABLE driver_payouts (
    id                    VARCHAR(255) PRIMARY KEY,
    driver_id             VARCHAR(255),
    requested_amount      DOUBLE PRECISION,
    status                VARCHAR(255) NOT NULL DEFAULT 'PENDING',
    account_holder_name   VARCHAR(255),
    account_number        VARCHAR(255),
    ifsc_code             VARCHAR(255),
    bank_name             VARCHAR(255),
    utr_number            VARCHAR(255),
    admin_notes           VARCHAR(255),
    requested_at          TIMESTAMP,
    processed_at          TIMESTAMP
);
CREATE INDEX idx_driver_payouts_driver_status ON driver_payouts(driver_id, status);
CREATE INDEX idx_driver_payouts_driver_requested ON driver_payouts(driver_id, requested_at);

-- ── driver_payments ──────────────────────────────────────────────────────────
CREATE TABLE driver_payments (
    id                    VARCHAR(255) PRIMARY KEY,
    driver_id             VARCHAR(255),
    ride_id               VARCHAR(255),
    amount                DOUBLE PRECISION,
    platform_fee_percent  DOUBLE PRECISION,
    platform_fee          DOUBLE PRECISION,
    net_amount            DOUBLE PRECISION,
    created_at            TIMESTAMP
);
CREATE INDEX idx_driver_payments_driver_created ON driver_payments(driver_id, created_at);
CREATE INDEX idx_driver_payments_ride_id ON driver_payments(ride_id);

-- ── driver_safety_deposits ───────────────────────────────────────────────────
CREATE TABLE driver_safety_deposits (
    id             VARCHAR(255) PRIMARY KEY,
    driver_id      VARCHAR(255),
    amount         DOUBLE PRECISION,
    payment_mode   VARCHAR(255),
    status         VARCHAR(255) NOT NULL DEFAULT 'PENDING',
    transaction_id VARCHAR(255),
    created_at     TIMESTAMP
);
CREATE INDEX idx_driver_safety_deposits_driver_id ON driver_safety_deposits(driver_id);

-- ── split_fare_requests ──────────────────────────────────────────────────────
CREATE TABLE split_fare_requests (
    id                 VARCHAR(255) PRIMARY KEY,
    ride_id            VARCHAR(255),
    initiator_user_id  VARCHAR(255),
    total_amount       DOUBLE PRECISION,
    status             VARCHAR(255) NOT NULL DEFAULT 'PENDING',
    created_at         TIMESTAMP
);
CREATE INDEX idx_split_fare_requests_ride_id ON split_fare_requests(ride_id);
CREATE INDEX idx_split_fare_requests_initiator ON split_fare_requests(initiator_user_id);

CREATE TABLE split_fare_participants (
    split_fare_request_id VARCHAR(255) NOT NULL REFERENCES split_fare_requests(id),
    participant_order     INTEGER NOT NULL,
    phone_number           VARCHAR(255),
    name                   VARCHAR(255),
    share_amount           DOUBLE PRECISION,
    payment_status         VARCHAR(255) NOT NULL DEFAULT 'PENDING',
    paid_at                TIMESTAMP,
    PRIMARY KEY (split_fare_request_id, participant_order)
);

-- ── payments (new — HLD §17.1/§17.2 payment state machine) ───────────────────
CREATE TABLE payments (
    internal_payment_id VARCHAR(255) PRIMARY KEY,
    provider_payment_id VARCHAR(255),
    provider_order_id   VARCHAR(255),
    ride_id             VARCHAR(255) NOT NULL,
    customer_id         VARCHAR(255) NOT NULL,
    driver_id           VARCHAR(255),
    amount              DOUBLE PRECISION NOT NULL,
    currency            VARCHAR(8) NOT NULL DEFAULT 'INR',
    status              VARCHAR(32) NOT NULL DEFAULT 'INITIATED',
    method              VARCHAR(16) NOT NULL,
    idempotency_key     VARCHAR(255) NOT NULL,
    failure_reason      VARCHAR(500),
    invoice_number      VARCHAR(64),
    created_at          TIMESTAMP NOT NULL,
    updated_at          TIMESTAMP NOT NULL,
    version             BIGINT NOT NULL DEFAULT 0
);
-- Unique index on idempotency_key is the safety net for POST /payments retries.
CREATE UNIQUE INDEX ux_payments_idempotency_key ON payments(idempotency_key);
CREATE UNIQUE INDEX ux_payments_provider_order_id ON payments(provider_order_id) WHERE provider_order_id IS NOT NULL;
CREATE INDEX idx_payments_ride_id ON payments(ride_id);
CREATE INDEX idx_payments_customer_id ON payments(customer_id);
CREATE INDEX idx_payments_provider_payment_id ON payments(provider_payment_id);

-- ── payment_webhook_events (new — webhook idempotency ledger, HLD §18) ───────
CREATE TABLE payment_webhook_events (
    id                 VARCHAR(255) PRIMARY KEY,
    provider_event_id  VARCHAR(255) NOT NULL,
    payment_id         VARCHAR(255),
    event_type         VARCHAR(64),
    processed_at       TIMESTAMP NOT NULL
);
-- The dedupe guarantee: a webhook delivered twice with the same event id can only
-- ever insert one row here — the second attempt hits this unique index/repository
-- lookup and is short-circuited before any Payment state change.
CREATE UNIQUE INDEX ux_payment_webhook_events_provider_event_id ON payment_webhook_events(provider_event_id);
