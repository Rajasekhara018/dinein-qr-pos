-- =====================================================================
-- Payment rows were tenant-scoped only indirectly (via order_id). Adds
-- restaurant_id directly, matching every other tenant-owned table, so
-- the Hibernate tenant filter (see TenantOwnedEntity) also covers
-- payments and any future restaurant-scoped payment query doesn't have
-- to join through orders just to stay tenant-safe.
-- =====================================================================

ALTER TABLE payment ADD COLUMN restaurant_id BIGINT;

UPDATE payment p SET restaurant_id = o.restaurant_id
FROM orders o
WHERE o.id = p.order_id;

ALTER TABLE payment ALTER COLUMN restaurant_id SET NOT NULL;

CREATE INDEX idx_payment_restaurant ON payment (restaurant_id);
