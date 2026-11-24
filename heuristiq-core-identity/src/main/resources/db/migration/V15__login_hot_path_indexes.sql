-- Hot-path indexes found missing by the capacity review (Rider_UAM/docs/capacity/query-review.md).
-- Each one backs a query that runs on every login or refresh and would otherwise scan the whole table
-- (about 10 million rows in the planned deployment). All are plain, non-unique indexes: nothing here can fail
-- on existing data and nothing changes behaviour, only the plan.
--
--   idx_uam_users_user_name_upper / idx_uam_users_email_upper
--       Login looks the account up with findByUserNameIgnoreCase / findByEmailIgnoreCase, which Spring Data turns
--       into  upper(user_name) = upper(?)  /  upper(email) = upper(?).  The unique constraints on the raw columns
--       (uk_uam_user_user_name, uk_uam_user_email) cannot serve an upper() comparison, so both lookups scanned
--       uam_users. The login identifier is tried as user name, then email, then phone, so one failed or
--       phone-based login paid for two full scans.
--   idx_uam_users_number
--       V14 dropped the unique(number) constraint (and with it its index). The legacy phone fallback
--       (findAllByPhoneNumber_Number, used when no row carries the canonical phone_e164) became a full scan.
--   idx_user_credentials_user_provider
--       Password check: findByUserIdAndProvider(userId, LOCAL). user_credentials had no index on user_id
--       (only unique(provider, provider_user_id), which does not lead with user_id).
--   idx_refresh_tokens_token_hash
--       Token refresh: findByTokenHash(sha256 of the presented token). No index existed.
--   idx_refresh_tokens_principal
--       Logout and "revoke all": findByPrincipalIdAndRevokedFalse(userId). No index existed.
--
-- Locking: CREATE INDEX (not CONCURRENTLY) holds a SHARE lock on the table, so writes to it wait while the index
-- builds. Flyway runs this file inside one transaction, and CONCURRENTLY cannot run inside a transaction. On a
-- database that already holds millions of rows, create the same indexes by hand first, outside Flyway, with
-- CREATE INDEX CONCURRENTLY and exactly these names (see the runbook in query-review.md); the IF NOT EXISTS
-- below then makes this migration a no-op. A failed CONCURRENTLY build leaves an INVALID index of the same
-- name that IF NOT EXISTS would silently accept: drop it and rebuild it before migrating.

create index if not exists idx_uam_users_user_name_upper on uam_users (upper(user_name));
create index if not exists idx_uam_users_email_upper on uam_users (upper(email));
create index if not exists idx_uam_users_number on uam_users (number);
create index if not exists idx_user_credentials_user_provider on user_credentials (user_id, provider);
create index if not exists idx_refresh_tokens_token_hash on refresh_tokens (token_hash);
create index if not exists idx_refresh_tokens_principal on refresh_tokens (principal_id);
