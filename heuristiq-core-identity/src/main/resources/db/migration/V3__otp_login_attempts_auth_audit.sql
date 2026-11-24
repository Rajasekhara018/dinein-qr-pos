-- Postgres tables for the three stores that previously existed only in MongoDB
-- (MongoOtpStore, LoginAttemptService, AuthAuditService), so UAM can run with no Mongo at all.

create table uam_otp_challenges (
    otp_key    varchar(255)                not null,
    otp_value  varchar(255)                not null,
    expires_at timestamp(6) with time zone not null,
    primary key (otp_key)
);
create index idx_uam_otp_challenges_expires_at on uam_otp_challenges (expires_at);

create table uam_login_attempts (
    username     varchar(255)                not null,
    failures     integer                     not null,
    locked_until timestamp(6) with time zone,
    expires_at   timestamp(6) with time zone not null,
    primary key (username)
);

create table uam_auth_audit_events (
    id         varchar(255)                not null,
    event_type varchar(255)                not null,
    user_id    varchar(255),
    username   varchar(255),
    reason     varchar(1024),
    roles      varchar(2048),
    created_at timestamp(6) with time zone not null,
    primary key (id)
);
create index idx_uam_auth_audit_events_created_at on uam_auth_audit_events (created_at);
