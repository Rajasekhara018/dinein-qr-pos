-- Richer FCM device registrations: who/what is behind a token, and when it was last seen.
ALTER TABLE nms_device_tokens
    ADD COLUMN user_type    VARCHAR(64),
    ADD COLUMN app          VARCHAR(64),
    ADD COLUMN device_model VARCHAR(255),
    ADD COLUMN os_version   VARCHAR(128),
    ADD COLUMN app_version  VARCHAR(64),
    ADD COLUMN locale       VARCHAR(32),
    ADD COLUMN timezone     VARCHAR(64),
    ADD COLUMN last_seen_at TIMESTAMP;
