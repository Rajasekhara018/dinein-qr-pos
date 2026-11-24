-- Driver earnings, payouts, deposits, incentives and split-fare amounts become exact decimals (were DOUBLE PRECISION).
-- Existing values are rounded to 2 decimals (the fee percentage keeps 3).
ALTER TABLE driver_payments ALTER COLUMN amount TYPE NUMERIC(14,2) USING ROUND(amount::numeric, 2);
ALTER TABLE driver_payments ALTER COLUMN platform_fee_percent TYPE NUMERIC(7,3) USING ROUND(platform_fee_percent::numeric, 3);
ALTER TABLE driver_payments ALTER COLUMN platform_fee TYPE NUMERIC(14,2) USING ROUND(platform_fee::numeric, 2);
ALTER TABLE driver_payments ALTER COLUMN net_amount TYPE NUMERIC(14,2) USING ROUND(net_amount::numeric, 2);
ALTER TABLE driver_payouts ALTER COLUMN requested_amount TYPE NUMERIC(14,2) USING ROUND(requested_amount::numeric, 2);
ALTER TABLE driver_safety_deposits ALTER COLUMN amount TYPE NUMERIC(14,2) USING ROUND(amount::numeric, 2);
ALTER TABLE driver_incentives ALTER COLUMN bonus_amount DROP DEFAULT;
ALTER TABLE driver_incentives ALTER COLUMN bonus_amount TYPE NUMERIC(14,2) USING ROUND(bonus_amount::numeric, 2);
ALTER TABLE driver_incentives ALTER COLUMN bonus_amount SET DEFAULT 0;
ALTER TABLE split_fare_requests ALTER COLUMN total_amount TYPE NUMERIC(14,2) USING ROUND(total_amount::numeric, 2);
ALTER TABLE split_fare_participants ALTER COLUMN share_amount TYPE NUMERIC(14,2) USING ROUND(share_amount::numeric, 2);
