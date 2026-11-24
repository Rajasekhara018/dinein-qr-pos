-- V17: a refund whose destination is WALLET is now credited through Rider_WalletService (POST /internal/refund-credits,
-- idempotent on the refund id) instead of being written to the legacy wallet_transactions table, which is not written
-- any more. The wallet's journal id is kept on the refund row as the reference of the credit (null for gateway
-- refunds, for refunds credited by the old code, and while the credit is still outstanding).
ALTER TABLE payment_refunds ADD COLUMN IF NOT EXISTS wallet_journal_id VARCHAR(255);

COMMENT ON TABLE wallet_transactions IS 'Legacy ledger, read-only for reconciliation (top-ups and refunds no longer write here). Balances live in Rider_WalletService.';
