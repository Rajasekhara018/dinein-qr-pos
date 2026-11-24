-- Existing paid sessions retain their original compatibility behavior; new sessions snapshot the origin app.
ALTER TABLE wallet_topup_sessions ADD COLUMN app_code VARCHAR(100);
