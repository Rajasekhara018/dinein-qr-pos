-- V25: the driver payment record takes its platform fee only from the ride's own fare split (Rider_RideService, decided by the
-- central commission plan). The fixed app.platform.fee-percent fallback is gone (docs/FRANCHISE_PLAN.md, step 10): when the
-- split is not available yet the row is written with an empty fee and net (PENDING_SPLIT) and a scheduled job asks Ride again;
-- a split for another fare than the one captured is SPLIT_MISMATCH (empty, for a person to check).
--
-- Rows written before V25 keep their amounts and read as SPLIT (some of them were computed with the retired fixed percent).

alter table driver_payments add column split_status varchar(20) not null default 'SPLIT';
alter table driver_payments
    add constraint ck_driver_payments_split_status check (split_status in ('SPLIT', 'PENDING_SPLIT', 'SPLIT_MISMATCH')),
    add constraint ck_driver_payments_split_amounts check (split_status = 'SPLIT' or (platform_fee is null and net_amount is null));

-- The retry job reads the oldest pending rows.
create index ix_driver_payments_pending_split on driver_payments (created_at) where split_status = 'PENDING_SPLIT';
