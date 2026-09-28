-- =====================================================================
-- Multi-tenant foundation: introduces the `restaurant` (tenant) table
-- and a restaurant_id column on every tenant-owned table, backfilled
-- to the single restaurant that already exists.
--
-- Schema-only step: application code still implicitly operates against
-- restaurant id = 1 until the tenant-scoping application layer lands.
-- =====================================================================

CREATE TABLE restaurant (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(100) NOT NULL,
    slug            VARCHAR(60)  NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_restaurant_slug ON restaurant (lower(slug));

INSERT INTO restaurant (id, name, slug)
VALUES (1, coalesce((SELECT name FROM restaurant_settings WHERE id = 1), 'My Restaurant'), 'default');
SELECT setval(pg_get_serial_sequence('restaurant', 'id'), 1);

-- ---------------------------------------------------------------------
-- restaurant_settings: one settings row per restaurant going forward.
-- ---------------------------------------------------------------------
ALTER TABLE restaurant_settings ADD COLUMN restaurant_id BIGINT REFERENCES restaurant (id);
UPDATE restaurant_settings SET restaurant_id = 1;
ALTER TABLE restaurant_settings ALTER COLUMN restaurant_id SET NOT NULL;
CREATE UNIQUE INDEX uq_restaurant_settings_restaurant ON restaurant_settings (restaurant_id);

-- ---------------------------------------------------------------------
-- Menu
-- ---------------------------------------------------------------------
ALTER TABLE category ADD COLUMN restaurant_id BIGINT REFERENCES restaurant (id);
UPDATE category SET restaurant_id = 1;
ALTER TABLE category ALTER COLUMN restaurant_id SET NOT NULL;
CREATE INDEX idx_category_restaurant ON category (restaurant_id);

ALTER TABLE item ADD COLUMN restaurant_id BIGINT REFERENCES restaurant (id);
UPDATE item SET restaurant_id = 1;
ALTER TABLE item ALTER COLUMN restaurant_id SET NOT NULL;
CREATE INDEX idx_item_restaurant ON item (restaurant_id);

-- item_variant / addon are reached only through item_id and stay unscoped directly.

-- ---------------------------------------------------------------------
-- Tables & QR
-- ---------------------------------------------------------------------
ALTER TABLE dining_table ADD COLUMN restaurant_id BIGINT REFERENCES restaurant (id);
UPDATE dining_table SET restaurant_id = 1;
ALTER TABLE dining_table ALTER COLUMN restaurant_id SET NOT NULL;
CREATE INDEX idx_dining_table_restaurant ON dining_table (restaurant_id);

-- ---------------------------------------------------------------------
-- Orders
-- ---------------------------------------------------------------------
ALTER TABLE orders ADD COLUMN restaurant_id BIGINT REFERENCES restaurant (id);
UPDATE orders SET restaurant_id = 1;
ALTER TABLE orders ALTER COLUMN restaurant_id SET NOT NULL;
CREATE INDEX idx_orders_restaurant ON orders (restaurant_id);

-- order_item / order_item_addon / payment / payment_event are reached only
-- through order_id and stay unscoped directly.

-- ---------------------------------------------------------------------
-- Staff & devices
-- ---------------------------------------------------------------------
ALTER TABLE staff_user ADD COLUMN restaurant_id BIGINT REFERENCES restaurant (id);
UPDATE staff_user SET restaurant_id = 1;
ALTER TABLE staff_user ALTER COLUMN restaurant_id SET NOT NULL;
CREATE INDEX idx_staff_user_restaurant ON staff_user (restaurant_id);

ALTER TABLE device_token ADD COLUMN restaurant_id BIGINT REFERENCES restaurant (id);
UPDATE device_token SET restaurant_id = 1;
ALTER TABLE device_token ALTER COLUMN restaurant_id SET NOT NULL;
CREATE INDEX idx_device_token_restaurant ON device_token (restaurant_id);

-- refresh_token is reached only through staff_user_id and stays unscoped directly.
