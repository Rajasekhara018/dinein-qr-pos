-- V16: Rider_WalletService owns every user balance and ledger entry. Payment keeps only the gateway side of a
-- top-up (checkout session, webhook/poll verification, gateway reference and failure reason) and, once a top-up is
-- verified as paid, credits the wallet through one idempotent call keyed by the top-up id.
--
-- A verified top-up whose credit has not gone through yet sits in status PAID_AWAITING_CREDIT (status is
-- VARCHAR(32), no constraint change needed); a bounded retry job books it later. These columns track that.
ALTER TABLE wallet_topup_sessions ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE wallet_topup_sessions ADD COLUMN IF NOT EXISTS credit_attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE wallet_topup_sessions ADD COLUMN IF NOT EXISTS credit_next_attempt_at TIMESTAMP;
ALTER TABLE wallet_topup_sessions ADD COLUMN IF NOT EXISTS credit_last_error VARCHAR(500);
ALTER TABLE wallet_topup_sessions ADD COLUMN IF NOT EXISTS credited_at TIMESTAMP;
ALTER TABLE wallet_topup_sessions ADD COLUMN IF NOT EXISTS wallet_journal_id VARCHAR(255);
CREATE INDEX IF NOT EXISTS ix_wallet_topup_sessions_status_next ON wallet_topup_sessions(status, credit_next_attempt_at);

-- Deliberately NOT dropped: wallet_accounts and wallet_transactions. After this release no top-up writes to them.
-- They stay as a read-only record for reconciliation against Rider_WalletService (see ops/README.md,
-- "Moving old balances to the wallet"). Refund-to-wallet credits (PaymentRefundService) still write
-- wallet_transactions until Rider_WalletService offers an endpoint for them; that is an open decision.
COMMENT ON TABLE wallet_accounts IS 'Legacy, read-only for reconciliation. Balances live in Rider_WalletService.';
COMMENT ON TABLE wallet_transactions IS 'Legacy ledger, read-only for reconciliation (top-ups no longer write here). Balances live in Rider_WalletService.';
