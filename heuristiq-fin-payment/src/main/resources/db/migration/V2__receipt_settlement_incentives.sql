-- V2: receipt/settlement columns on `payments`, ShedLock's own lock table (required by
-- shedlock-provider-jdbc-template for SettlementScheduler), and the new `driver_incentives`
-- table ported from Rider_BookingsAPI's driver.incentive package.

-- ── payments: receipt (HLD step 22) + settlement (HLD §17.2 CAPTURED -> SETTLED) ────────────
ALTER TABLE payments ADD COLUMN receipt_number VARCHAR(64);
ALTER TABLE payments ADD COLUMN receipt_generated_at TIMESTAMP;
ALTER TABLE payments ADD COLUMN settled_at TIMESTAMP;
CREATE UNIQUE INDEX ux_payments_receipt_number ON payments(receipt_number) WHERE receipt_number IS NOT NULL;

-- ── shedlock ─────────────────────────────────────────────────────────────────────────────────
-- Required by net.javacrumbs.shedlock:shedlock-provider-jdbc-template's JdbcTemplateLockProvider
-- (ShedLockConfig), which guards SettlementScheduler against duplicate concurrent execution.
CREATE TABLE shedlock (
    name       VARCHAR(64) NOT NULL PRIMARY KEY,
    lock_until TIMESTAMP NOT NULL,
    locked_at  TIMESTAMP NOT NULL,
    locked_by  VARCHAR(255) NOT NULL
);

-- ── driver_incentives ────────────────────────────────────────────────────────────────────────
-- Ported from Rider_BookingsAPI's driver.incentive package (DriverIncentive entity +
-- DriverIncentiveService + IncentiveRule* strategy implementations). This service only ports the
-- TRIP_COUNT rule (see DriverIncentiveService/TripCountIncentiveRule) — HIGH_RATING and
-- ZERO_CANCELLATION depend on driver rating / ride-cancellation data this service does not own
-- (see README's "Deliberately deferred" section).
CREATE TABLE driver_incentives (
    id               VARCHAR(255) PRIMARY KEY,
    driver_id        VARCHAR(255) NOT NULL,
    rule_name        VARCHAR(255) NOT NULL,
    description      VARCHAR(255),
    bonus_amount     DOUBLE PRECISION NOT NULL DEFAULT 0,
    week_start_date  DATE NOT NULL,
    awarded_at       TIMESTAMP NOT NULL,
    status           VARCHAR(32) NOT NULL DEFAULT 'PENDING'
);
CREATE INDEX idx_driver_incentives_driver_id ON driver_incentives(driver_id);
-- A driver can only be awarded a given rule once per week — the same guarantee
-- DriverIncentiveService enforces in code (alreadyAwardedRules check), backstopped here.
CREATE UNIQUE INDEX ux_driver_incentives_driver_rule_week ON driver_incentives(driver_id, rule_name, week_start_date);
