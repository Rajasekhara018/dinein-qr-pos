-- Payments raised by another Rider application (for example the matrimony app) rather than for a ride.
-- Only who asked and which of their records it is for; nothing else about the caller's domain is kept here.
ALTER TABLE payments ADD COLUMN source_service VARCHAR(64);
ALTER TABLE payments ADD COLUMN source_reference VARCHAR(128);
CREATE INDEX idx_payments_source ON payments(source_service, source_reference);
