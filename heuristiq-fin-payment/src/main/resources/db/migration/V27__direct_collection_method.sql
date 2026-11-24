-- V27: direct collections (docs/DRIVER_FRANCHISE_SUBSCRIPTION_PLAN.md, D12-D13). Cash and UPI rides are paid to the driver
-- directly; Rider_RideService records each confirmed collection here (POST /api/v1/internal/payments/direct-collections) as a
-- CAPTURED payment with method CASH or the new UPI_DIRECT (paid to the driver's own UPI id, no gateway), one per ride under the
-- idempotency key direct-collection:<rideId> (payments.idempotency_key is already unique).
--
-- The method column now has three values; the constraint keeps anything else out. NOT VALID: rows written before it are not
-- re-checked (they were written by the same enum), so adding it takes no long lock on a large table.
ALTER TABLE payments DROP CONSTRAINT IF EXISTS ck_payments_method;
ALTER TABLE payments ADD CONSTRAINT ck_payments_method CHECK (method IN ('CASH', 'RAZORPAY', 'UPI_DIRECT')) NOT VALID;
