-- Refresh-token rotation hardening (README "Refresh tokens", docs/RIDE_FLOW_FIXES_2026-11.md row B2).
--   family_id          one sign-in's chain of rotated tokens; reuse of a rotated token outside the grace window revokes
--                      every active token of the family. Null on tokens issued before this migration.
--   successor_token_id token id of the token that replaced this one, so a replay inside the grace window is answered with
--                      the family's current token instead of a new branch.
--   revoked_at         when the token was revoked; starts the grace window for ROTATED tokens.
--   grace_redemptions  how often a rotated token was redeemed inside the window (capped by configuration).
-- Nullable columns with no default: adding them rewrites nothing and takes only a brief lock.
alter table refresh_tokens add column if not exists family_id varchar(255);
alter table refresh_tokens add column if not exists successor_token_id varchar(255);
alter table refresh_tokens add column if not exists revoked_at timestamp(6);
alter table refresh_tokens add column if not exists grace_redemptions integer;
