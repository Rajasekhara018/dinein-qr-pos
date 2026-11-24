-- Sign in with fingerprint / Face ID (device-key design). The phone keeps a private key that only the person's face or
-- finger unlocks; UAM keeps its public key per app installation, hands out one-time challenges and counts failed sign-ins.
-- All three are UAM's own data, shared by every UAM instance. MongoDB keeps collections of the same names and shape.

-- One row per enrolment. A credential belongs to one user (identity, not organization data); tenant_id only records the
-- organization of the session that enrolled it, for the audit trail. Rows are never deleted: REVOKED / EXPIRED stay as history.
create table if not exists biometric_credentials (
    credential_id    varchar(64)  not null,
    user_id          varchar(100) not null,
    tenant_id        varchar(100),
    application_code varchar(100) not null,
    installation_id  varchar(64)  not null,
    public_key       text         not null,
    key_algorithm    varchar(30)  not null,
    platform         varchar(20)  not null,
    device_name      varchar(100),
    status           varchar(20)  not null,
    revoked_reason   varchar(40),
    created_at       timestamptz  not null,
    last_used_at     timestamptz,
    expires_at       timestamptz  not null,
    revoked_at       timestamptz,
    updated_at       timestamptz  not null,
    constraint pk_biometric_credentials primary key (credential_id),
    constraint ck_biometric_credentials_status check (status in ('ACTIVE', 'REVOKED', 'EXPIRED')),
    constraint ck_biometric_credentials_algorithm check (key_algorithm in ('EC_P256_SHA256', 'RSA_2048_SHA256')),
    constraint ck_biometric_credentials_platform check (platform in ('ANDROID', 'IOS'))
);
create index if not exists idx_biometric_credentials_user_app_installation
    on biometric_credentials (user_id, application_code, installation_id);
create index if not exists idx_biometric_credentials_installation on biometric_credentials (installation_id);
create index if not exists idx_biometric_credentials_status on biometric_credentials (status);
-- At most one ACTIVE credential per application and installation: two racing enrolments cannot both win.
create unique index if not exists uk_biometric_credentials_active_installation
    on biometric_credentials (application_code, installation_id) where status = 'ACTIVE';

-- One-time sign-in challenges (valid 60 s by default, used at most once). Rows that ended over an hour ago are removed when
-- a new challenge is stored.
create table if not exists biometric_challenges (
    challenge_id    varchar(64) not null,
    installation_id varchar(64) not null,
    challenge       varchar(64) not null,
    created_at      timestamptz not null,
    expires_at      timestamptz not null,
    used_at         timestamptz,
    constraint pk_biometric_challenges primary key (challenge_id)
);
create index if not exists idx_biometric_challenges_expires on biometric_challenges (expires_at);

-- Failed biometric sign-ins per installation in a fixed window from the first failure (5 in 15 minutes by default).
create table if not exists biometric_signin_attempts (
    installation_id   varchar(64) not null,
    failures          integer     not null,
    window_started_at timestamptz not null,
    expires_at        timestamptz not null,
    constraint pk_biometric_signin_attempts primary key (installation_id)
);
create index if not exists idx_biometric_signin_attempts_expires on biometric_signin_attempts (expires_at);

-- How a session was signed in: null for an interactive sign-in (password, one-time code, Google, external provider),
-- BIOMETRIC for a device-key sign-in. Set once at sign-in; enrolment without a password needs a recent interactive one.
-- A nullable column without a default: a catalog-only change, no table rewrite.
alter table user_sessions add column if not exists auth_method varchar(30);
