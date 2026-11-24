-- V15: the organization each payment record belongs to (the caller's session organization when it was created).
-- NULL for every existing row (stored before organizations): such a row stays visible to every caller until it is
-- backfilled with its organization, e.g.  UPDATE payments SET tenant_id = '<org id>' WHERE tenant_id IS NULL;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(255);
ALTER TABLE payment_refunds ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(255);
ALTER TABLE wallet_topup_sessions ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(255);
ALTER TABLE driver_payments ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(255);
ALTER TABLE driver_payouts ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(255);
ALTER TABLE driver_incentives ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(255);
ALTER TABLE driver_safety_deposits ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(255);
ALTER TABLE split_fare_requests ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(255);
ALTER TABLE wallet_transactions ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(255);
CREATE INDEX IF NOT EXISTS ix_payments_tenant ON payments(tenant_id);
CREATE INDEX IF NOT EXISTS ix_driver_payments_tenant ON driver_payments(tenant_id);
CREATE INDEX IF NOT EXISTS ix_driver_payouts_tenant ON driver_payouts(tenant_id);
CREATE INDEX IF NOT EXISTS ix_driver_incentives_tenant ON driver_incentives(tenant_id);
CREATE INDEX IF NOT EXISTS ix_driver_safety_deposits_tenant ON driver_safety_deposits(tenant_id);
CREATE INDEX IF NOT EXISTS ix_split_fare_requests_tenant ON split_fare_requests(tenant_id);
CREATE INDEX IF NOT EXISTS ix_wallet_transactions_tenant ON wallet_transactions(tenant_id);
CREATE INDEX IF NOT EXISTS ix_payment_refunds_tenant ON payment_refunds(tenant_id);
CREATE INDEX IF NOT EXISTS ix_wallet_topup_sessions_tenant ON wallet_topup_sessions(tenant_id);
