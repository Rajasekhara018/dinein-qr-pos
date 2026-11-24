-- Wallet ledger amounts become exact decimals (they were DOUBLE PRECISION). Existing values are rounded to 2 decimals,
-- which is how WalletService has always posted them.
ALTER TABLE wallet_transactions ALTER COLUMN amount TYPE NUMERIC(14,2) USING ROUND(amount::numeric, 2);
ALTER TABLE wallet_topup_sessions ALTER COLUMN amount TYPE NUMERIC(14,2) USING ROUND(amount::numeric, 2);
