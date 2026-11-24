-- Go-live guard, owner team and readiness alerts (docs/APPLICATION_READINESS.md, stage 3).
-- The owner team, the SUPER_ADMIN go-live override and the alert throttle state live on the organization's own link to an
-- application (never on the shared application row).
alter table tenant_applications add column if not exists owner_team_user_ids varchar(2000);
alter table tenant_applications add column if not exists go_live_override_by varchar(100);
alter table tenant_applications add column if not exists go_live_override_at timestamp(6);
alter table tenant_applications add column if not exists go_live_override_reason varchar(500);
alter table tenant_applications add column if not exists go_live_override_steps varchar(1000);
alter table tenant_applications add column if not exists last_blocked_alert_at timestamp(6);
alter table tenant_applications add column if not exists last_blocked_alert_fingerprint varchar(64);
alter table tenant_applications add column if not exists last_refused_alert_at timestamp(6);
alter table tenant_applications add column if not exists last_refused_alert_fingerprint varchar(64);

-- Database lease so the scheduled readiness re-check runs on ONE UAM instance at a time. A row is taken with an atomic
-- upsert and simply expires if its holder dies.
create table if not exists scheduler_locks (
    name         varchar(100) primary key,
    holder       varchar(100) not null,
    locked_until timestamptz  not null
);
