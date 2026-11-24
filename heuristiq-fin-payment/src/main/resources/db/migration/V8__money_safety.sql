-- V8: money-safety hardening. Every statement is idempotent and tolerates existing data; no history
-- is rewritten.

-- ── Wallet: one posting per business event ───────────────────────────────────
-- idempotency_key is NULL on every existing row (the unique index ignores NULLs), so existing data
-- can never violate it. New postings carry REFUND:<refund reference>, TOPUP:<topup id> or
-- RIDE_PAYMENT:<ride id>.
ALTER TABLE wallet_transactions ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(255);
CREATE UNIQUE INDEX IF NOT EXISTS ux_wallet_transactions_idempotency_key
    ON wallet_transactions(idempotency_key) WHERE idempotency_key IS NOT NULL;

-- One row per customer, used only as the row lock that serialises that customer's wallet postings.
-- The balance is always derived from the full ledger. Rows are created on demand.
CREATE TABLE IF NOT EXISTS wallet_accounts (
    user_id    VARCHAR(255) PRIMARY KEY,
    created_at TIMESTAMP
);

-- ── Refund ledger ────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS payment_refunds (
    id                VARCHAR(255) PRIMARY KEY,
    refund_reference  VARCHAR(100) NOT NULL,
    payment_id        VARCHAR(255) NOT NULL,
    amount            DOUBLE PRECISION NOT NULL,
    destination       VARCHAR(16) NOT NULL,
    status            VARCHAR(16) NOT NULL,
    gateway_refund_id VARCHAR(255),
    gateway_status    VARCHAR(64),
    reason            VARCHAR(500),
    failure_reason    VARCHAR(500),
    created_by        VARCHAR(255),
    created_at        TIMESTAMP,
    updated_at        TIMESTAMP,
    version           BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_payment_refunds_reference ON payment_refunds(refund_reference);
CREATE INDEX IF NOT EXISTS idx_payment_refunds_payment ON payment_refunds(payment_id, status);

-- ── payments.provider_payment_id must identify one payment ───────────────────
-- Created only when no duplicate values exist; otherwise the migration succeeds without the index and
-- logs a notice. To finish: resolve the duplicate provider_payment_id rows, then run
--   CREATE UNIQUE INDEX IF NOT EXISTS ux_payments_provider_payment_id ON payments(provider_payment_id)
--       WHERE provider_payment_id IS NOT NULL AND provider_payment_id <> '';
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM payments
               WHERE provider_payment_id IS NOT NULL AND provider_payment_id <> ''
               GROUP BY provider_payment_id HAVING COUNT(*) > 1) THEN
        RAISE NOTICE 'ux_payments_provider_payment_id not created: duplicate provider_payment_id values exist';
    ELSE
        CREATE UNIQUE INDEX IF NOT EXISTS ux_payments_provider_payment_id ON payments(provider_payment_id)
            WHERE provider_payment_id IS NOT NULL AND provider_payment_id <> '';
    END IF;
END $$;
