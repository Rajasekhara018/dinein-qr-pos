-- Payment and refund-ledger amounts become exact decimals (they were DOUBLE PRECISION); existing values are rounded
-- to 2 decimals, which is how they were always written.
ALTER TABLE payments ALTER COLUMN amount TYPE NUMERIC(14,2) USING ROUND(amount::numeric, 2);
ALTER TABLE payment_refunds ALTER COLUMN amount TYPE NUMERIC(14,2) USING ROUND(amount::numeric, 2);
