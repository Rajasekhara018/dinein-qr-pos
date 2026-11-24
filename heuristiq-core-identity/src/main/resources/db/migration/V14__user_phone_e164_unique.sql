-- One account per REAL phone number (country + national number), not per national digits alone.
-- Adds the canonical E.164 column, backfills it from the legacy (country_code, number) pair, drops the old
-- unique(number) constraint and enforces uniqueness on the canonical value. Existing rows are not otherwise rewritten.
--
-- Backfill rule (mirrors PhoneIdentity.legacyE164):
--   country code blank:  exactly 10 digits -> '+91' || digits ; otherwise NULL (not guessable)
--   country code present (digits, e.g. '91', '+91', '1'): '+' || code || digits
-- If two rows would get the same value, only the earliest (created_at, id) receives it; the later duplicate is left
-- NULL on purpose and must be resolved by an admin. Find such rows after migrating with:
--   select id, user_name, country_code, number from uam_users
--   where phone_e164 is null and number is not null and btrim(number) <> '';
alter table uam_users add column if not exists phone_e164 varchar(20);

with base as (
    select id, created_at,
           regexp_replace(coalesce(country_code, ''), '[^0-9]', '', 'g') as cc,
           regexp_replace(coalesce(number, ''), '[^0-9]', '', 'g') as n
    from uam_users
    where phone_e164 is null
),
cand as (
    select id, created_at,
           case
               when n = '' then null
               when cc = '' then case when n ~ '^[0-9]{10}$' then '+91' || n end
               else '+' || cc || n
           end as e164
    from base
),
ranked as (
    select id, e164,
           row_number() over (partition by e164 order by created_at nulls last, id) as rn
    from cand
    where e164 is not null and length(e164) <= 20
)
update uam_users u
set phone_e164 = r.e164
from ranked r
where u.id = r.id
  and r.rn = 1
  and not exists (select 1 from uam_users o where o.phone_e164 = r.e164);

alter table uam_users drop constraint if exists uk_uam_user_phone_number;

create unique index if not exists uk_uam_user_phone_e164 on uam_users (phone_e164) where phone_e164 is not null;
