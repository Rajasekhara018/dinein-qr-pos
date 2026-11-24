-- =====================================================================
-- category.name only needs to be unique within a restaurant, not
-- globally (item.name is already scoped via category_id, so it needs
-- no change).
-- =====================================================================

DROP INDEX uq_category_name;

CREATE UNIQUE INDEX uq_category_restaurant_name ON category (restaurant_id, lower(name));
