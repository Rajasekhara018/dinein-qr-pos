-- =====================================================================
-- dining_table.label only needs to be unique within a restaurant, not
-- globally. qr_token stays globally unique: it is a random opaque
-- lookup key, not a tenant-scoped concern.
-- =====================================================================

ALTER TABLE dining_table DROP CONSTRAINT dining_table_label_key;

CREATE UNIQUE INDEX uq_dining_table_restaurant_label ON dining_table (restaurant_id, label);
