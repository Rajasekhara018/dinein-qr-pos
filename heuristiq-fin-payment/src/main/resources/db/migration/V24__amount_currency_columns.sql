-- V24: every amount is stored with its own currency ({ code, value }). Every amount column of this service is already an
-- exact NUMERIC (V11, V12, V13) and keeps its type and value; this migration only records the currency.
--
-- payments and wallet_topup_sessions already carry one `currency` column (NOT NULL, default 'INR') that governs their single
-- amount, so they keep it and get no per-amount column.
--
-- Every other amount gets a <column>_currency varchar(3) next to it:
--  * payment_refunds.amount takes the currency of the payment it refunds (INR when that payment is missing or its code is
--    not three letters);
--  * driver_payments, driver_payouts, driver_safety_deposits, driver_incentives, split_fare_requests,
--    split_fare_participants and the legacy wallet_transactions were all written in INR, the service's default
--    (app.payment.currency).
-- A currency is set only where the amount is set, and a check keeps the two together from now on.
-- driver_incentives.bonus_amount is NOT NULL DEFAULT 0, so its currency defaults to INR as well: an insert that relies on the
-- amount's default still satisfies the check.
-- driver_payments.platform_fee_percent is a percentage, not an amount: it gets no currency.

alter table payment_refunds add column amount_currency varchar(3);
update payment_refunds r
   set amount_currency = case
           when r.amount is null then null
           else coalesce((select upper(p.currency) from payments p
                           where p.internal_payment_id = r.payment_id and length(p.currency) = 3), 'INR')
       end;
alter table payment_refunds
    add constraint ck_payment_refunds_amount_currency check ((amount is null) = (amount_currency is null));

alter table wallet_transactions add column amount_currency varchar(3);
update wallet_transactions set amount_currency = case when amount is not null then 'INR' end;
alter table wallet_transactions
    add constraint ck_wallet_transactions_amount_currency check ((amount is null) = (amount_currency is null));

alter table driver_payments
    add column amount_currency       varchar(3),
    add column platform_fee_currency varchar(3),
    add column net_amount_currency   varchar(3);
update driver_payments
   set amount_currency       = case when amount       is not null then 'INR' end,
       platform_fee_currency = case when platform_fee is not null then 'INR' end,
       net_amount_currency   = case when net_amount   is not null then 'INR' end;
alter table driver_payments
    add constraint ck_driver_payments_amount_currency       check ((amount       is null) = (amount_currency       is null)),
    add constraint ck_driver_payments_platform_fee_currency check ((platform_fee is null) = (platform_fee_currency is null)),
    add constraint ck_driver_payments_net_amount_currency   check ((net_amount   is null) = (net_amount_currency   is null));

alter table driver_payouts add column requested_amount_currency varchar(3);
update driver_payouts set requested_amount_currency = case when requested_amount is not null then 'INR' end;
alter table driver_payouts
    add constraint ck_driver_payouts_requested_amount_currency
        check ((requested_amount is null) = (requested_amount_currency is null));

alter table driver_safety_deposits add column amount_currency varchar(3);
update driver_safety_deposits set amount_currency = case when amount is not null then 'INR' end;
alter table driver_safety_deposits
    add constraint ck_driver_safety_deposits_amount_currency check ((amount is null) = (amount_currency is null));

alter table driver_incentives add column bonus_amount_currency varchar(3) default 'INR';
update driver_incentives set bonus_amount_currency = case when bonus_amount is not null then 'INR' end;
alter table driver_incentives
    add constraint ck_driver_incentives_bonus_amount_currency
        check ((bonus_amount is null) = (bonus_amount_currency is null));

alter table split_fare_requests add column total_amount_currency varchar(3);
update split_fare_requests set total_amount_currency = case when total_amount is not null then 'INR' end;
alter table split_fare_requests
    add constraint ck_split_fare_requests_total_amount_currency
        check ((total_amount is null) = (total_amount_currency is null));

alter table split_fare_participants add column share_amount_currency varchar(3);
update split_fare_participants set share_amount_currency = case when share_amount is not null then 'INR' end;
alter table split_fare_participants
    add constraint ck_split_fare_participants_share_amount_currency
        check ((share_amount is null) = (share_amount_currency is null));
