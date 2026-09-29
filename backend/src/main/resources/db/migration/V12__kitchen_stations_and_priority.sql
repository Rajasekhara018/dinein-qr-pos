-- =====================================================================
-- Kitchen stations are per-restaurant, not a fixed list: what a pizzeria
-- needs (Pizza, Beverage) differs from a cafe (Espresso Bar, Bakery) or
-- a dosa counter (Tawa, Chutney). Owners define their own during
-- onboarding or later from the admin panel; a category optionally
-- routes to one, and a placed order item snapshots the station id/name
-- at that moment (same reasoning as item_name/variant_name: a later
-- rename or re-route never changes a ticket already in the kitchen).
-- Priority: staff can flag an order for the kitchen to work first.
-- =====================================================================

CREATE TABLE kitchen_station (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    restaurant_id BIGINT NOT NULL,
    name VARCHAR(40) NOT NULL,
    display_order INT NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_kitchen_station_restaurant_name ON kitchen_station (restaurant_id, lower(name));

ALTER TABLE category ADD COLUMN station_id BIGINT REFERENCES kitchen_station (id);

ALTER TABLE order_item ADD COLUMN station_id BIGINT;
ALTER TABLE order_item ADD COLUMN station_name VARCHAR(40);

ALTER TABLE orders ADD COLUMN priority BOOLEAN NOT NULL DEFAULT false;
