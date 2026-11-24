-- ---------------------------------------------------------------------
-- Order types (DINE_IN / TAKEAWAY), WAITER staff role, staff-assisted orders and offline (counter) payments
-- ---------------------------------------------------------------------

-- WAITER role.
ALTER TABLE staff_user DROP CONSTRAINT staff_user_role_check;
ALTER TABLE staff_user ADD CONSTRAINT staff_user_role_check
    CHECK (role IN ('OWNER', 'MANAGER', 'KITCHEN', 'WAITER'));

-- Order type, and who placed the order. Guest orders carry a guest session; staff-assisted orders carry the staff
-- user instead (and have no guest session), so at least one of the two must be present.
ALTER TABLE orders
    ADD COLUMN order_type         VARCHAR(10) NOT NULL DEFAULT 'DINE_IN',
    ADD COLUMN placed_by_staff_id BIGINT      REFERENCES staff_user (id),
    ALTER COLUMN guest_session_id DROP NOT NULL;
ALTER TABLE orders ADD CONSTRAINT orders_order_type_check CHECK (order_type IN ('DINE_IN', 'TAKEAWAY'));
ALTER TABLE orders ADD CONSTRAINT orders_origin_check
    CHECK (guest_session_id IS NOT NULL OR placed_by_staff_id IS NOT NULL);

-- Offline payments: provider 'OFFLINE', method CASH | UPI_AT_COUNTER | CARD_AT_COUNTER, recorded by a staff user.
-- A refund of an offline payment is never sent to a gateway: it is marked MANUAL (hand the cash back).
ALTER TABLE payment ADD COLUMN recorded_by_staff_id BIGINT REFERENCES staff_user (id);
ALTER TABLE payment ADD CONSTRAINT payment_offline_recorded_by_check
    CHECK (provider <> 'OFFLINE' OR recorded_by_staff_id IS NOT NULL);
ALTER TABLE payment DROP CONSTRAINT payment_refund_status_check;
ALTER TABLE payment ADD CONSTRAINT payment_refund_status_check
    CHECK (refund_status IS NULL OR refund_status IN ('PENDING', 'PROCESSED', 'FAILED', 'MANUAL'));

-- Guests may choose takeaway unless the owner switches it off.
ALTER TABLE restaurant_settings ADD COLUMN takeaway_enabled BOOLEAN NOT NULL DEFAULT TRUE;
