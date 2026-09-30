-- =====================================================================
-- Kiosk upsell prompts: "add a dip with your burger", "a drink with a
-- combo", "a dessert at checkout". A rule suggests one menu item.
--   * trigger_item_id     -> fires when that item is added
--   * trigger_category_id -> fires when any item of that category is added
--   * neither             -> fires for any order (typically at checkout)
-- =====================================================================

CREATE TABLE kiosk_upsell_rule (
    id                  BIGSERIAL    PRIMARY KEY,
    restaurant_id       BIGINT       NOT NULL REFERENCES restaurant (id),
    trigger_item_id     BIGINT       REFERENCES item (id),
    trigger_category_id BIGINT       REFERENCES category (id),
    suggested_item_id   BIGINT       NOT NULL REFERENCES item (id),
    placement           VARCHAR(12)  NOT NULL CHECK (placement IN ('ITEM_ADDED', 'CHECKOUT')),
    message             VARCHAR(80),
    sort_order          INT          NOT NULL DEFAULT 0,
    active              BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CHECK (trigger_item_id IS NULL OR trigger_category_id IS NULL)
);

CREATE INDEX idx_kiosk_upsell_rule_restaurant ON kiosk_upsell_rule (restaurant_id, active);
