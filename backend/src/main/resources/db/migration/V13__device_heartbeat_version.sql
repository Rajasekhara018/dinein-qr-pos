-- =====================================================================
-- Decouples device liveness tracking from re-authentication: a device
-- now calls a dedicated heartbeat endpoint periodically, which also
-- lets it report its running app version for the admin device list.
-- =====================================================================

ALTER TABLE device_token ADD COLUMN application_version VARCHAR(20);
