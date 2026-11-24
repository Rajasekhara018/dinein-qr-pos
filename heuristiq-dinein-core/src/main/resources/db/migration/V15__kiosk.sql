-- =====================================================================
-- Self-order kiosk (Flutter Android app).
--   * kiosk_device: a paired kiosk. Admin creates the row and gets a one-time
--     pairing code; the kiosk exchanges it for a long-lived device token.
--     Only SHA-256 hashes of the code and the token are stored.
--   * kiosk_branding: per-restaurant look of the kiosk welcome page.
--   * orders.source: where an order came from. A kiosk order has neither a
--     guest session nor a staff user, so the origin check is widened.
-- =====================================================================

CREATE TABLE kiosk_device (
    id                  BIGSERIAL    PRIMARY KEY,
    restaurant_id       BIGINT       NOT NULL REFERENCES restaurant (id),
    name                VARCHAR(60)  NOT NULL,
    pairing_code_hash   CHAR(64),
    pairing_expires_at  TIMESTAMPTZ,
    token_hash          CHAR(64),
    paired_at           TIMESTAMPTZ,
    last_seen_at        TIMESTAMPTZ,
    application_version VARCHAR(20),
    revoked_at          TIMESTAMPTZ,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_kiosk_device_restaurant ON kiosk_device (restaurant_id);
CREATE UNIQUE INDEX ux_kiosk_device_token ON kiosk_device (token_hash) WHERE token_hash IS NOT NULL;
CREATE UNIQUE INDEX ux_kiosk_device_pairing ON kiosk_device (pairing_code_hash) WHERE pairing_code_hash IS NOT NULL;

CREATE TABLE kiosk_branding (
    id                   BIGSERIAL    PRIMARY KEY,
    restaurant_id        BIGINT       NOT NULL UNIQUE REFERENCES restaurant (id),
    kiosk_enabled        BOOLEAN      NOT NULL DEFAULT TRUE,
    primary_color        VARCHAR(7),
    secondary_color      VARCHAR(7),
    headline             VARCHAR(80),
    subtext              VARCHAR(120),
    start_button_label   VARCHAR(40),
    idle_timeout_seconds INT          CHECK (idle_timeout_seconds BETWEEN 15 AND 600),
    logo_image_id        BIGINT,
    background_image_id  BIGINT,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now()
);

ALTER TABLE orders ADD COLUMN source VARCHAR(10) NOT NULL DEFAULT 'GUEST_QR';
UPDATE orders SET source = 'STAFF' WHERE placed_by_staff_id IS NOT NULL;
ALTER TABLE orders ADD CONSTRAINT orders_source_check CHECK (source IN ('GUEST_QR', 'STAFF', 'KIOSK'));

ALTER TABLE orders DROP CONSTRAINT orders_origin_check;
ALTER TABLE orders ADD CONSTRAINT orders_origin_check
    CHECK (guest_session_id IS NOT NULL OR placed_by_staff_id IS NOT NULL OR source = 'KIOSK');
