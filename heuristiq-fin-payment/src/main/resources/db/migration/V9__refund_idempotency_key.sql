-- V9: caller-supplied Idempotency-Key on refunds. Same key + same payment returns the original refund;
-- the unique index is the backstop against concurrent duplicates. NULL for every existing row
-- (the partial index ignores NULLs), so existing data can never violate it.
ALTER TABLE payment_refunds ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(255);
CREATE UNIQUE INDEX IF NOT EXISTS ux_payment_refunds_idempotency_key
    ON payment_refunds(idempotency_key) WHERE idempotency_key IS NOT NULL;
