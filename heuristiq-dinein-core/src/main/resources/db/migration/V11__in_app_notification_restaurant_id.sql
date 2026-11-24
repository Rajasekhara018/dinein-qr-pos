-- =====================================================================
-- The staff in-app notification inbox had no restaurant_id at all,
-- so a MANAGER/OWNER/WAITER at one restaurant would see every other
-- restaurant's notifications mixed into their own inbox (queries only
-- filtered by role/recipient, never by tenant). Backfilled from the
-- related order, which every existing notification type carries.
-- =====================================================================

ALTER TABLE in_app_notification ADD COLUMN restaurant_id BIGINT;

UPDATE in_app_notification n SET restaurant_id = o.restaurant_id
FROM orders o
WHERE o.id = n.related_order_id;

-- Notifications somehow left without a resolvable order fall back to the original single-tenant restaurant,
-- consistent with every other backfill in this migration history.
UPDATE in_app_notification SET restaurant_id = 1 WHERE restaurant_id IS NULL;

ALTER TABLE in_app_notification ALTER COLUMN restaurant_id SET NOT NULL;

CREATE INDEX idx_in_app_notification_restaurant ON in_app_notification (restaurant_id);
