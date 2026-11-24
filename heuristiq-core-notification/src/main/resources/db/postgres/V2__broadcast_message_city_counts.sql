-- Columns added to BroadcastEntity (message body, target city, targeted / failed recipient
-- counts) without a migration -- Hibernate's ddl-auto=validate refused to start NMS until
-- nms_broadcasts has them.
ALTER TABLE nms_broadcasts ADD COLUMN IF NOT EXISTS message VARCHAR(4000);
ALTER TABLE nms_broadcasts ADD COLUMN IF NOT EXISTS city VARCHAR(255);
ALTER TABLE nms_broadcasts ADD COLUMN IF NOT EXISTS targeted_count INTEGER;
ALTER TABLE nms_broadcasts ADD COLUMN IF NOT EXISTS failed_count INTEGER;
