-- =====================================================================
-- A table can be reserved ahead of time (booked but not yet seated),
-- distinct from "occupied" (has an active order) and "active" (exists
-- at all / in service today).
-- =====================================================================

ALTER TABLE dining_table ADD COLUMN reserved_until TIMESTAMPTZ;
ALTER TABLE dining_table ADD COLUMN reserved_note VARCHAR(100);
