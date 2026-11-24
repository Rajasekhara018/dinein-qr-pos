-- PLT-01: OTPs are stored as a salted hash, with a wrong-attempt counter and per-target send throttling.
-- otp_value now holds the hash; rows written before this migration hold plaintext OTPs (5-minute
-- lifetime), so they are dropped rather than carried over -- users with a pending OTP request a new one.
delete from uam_otp_challenges;

alter table uam_otp_challenges alter column otp_value drop not null;
alter table uam_otp_challenges add column if not exists otp_salt         varchar(64);
alter table uam_otp_challenges add column if not exists otp_expires_at   timestamp(6) with time zone;
alter table uam_otp_challenges add column if not exists failed_attempts  integer not null default 0;
alter table uam_otp_challenges add column if not exists last_sent_at     timestamp(6) with time zone;
alter table uam_otp_challenges add column if not exists send_window_start timestamp(6) with time zone;
alter table uam_otp_challenges add column if not exists send_count       integer not null default 0;
alter table uam_otp_challenges add column if not exists version          bigint not null default 0;
