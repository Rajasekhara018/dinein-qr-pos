-- Per-organization, per-application choice of which readiness steps are Required (BLOCK) or only Recommended (WARN)
-- (docs/APPLICATION_READINESS.md, "Required or recommended"). Only differences from the code defaults are stored; a row equal
-- to the default is deleted. Every read filters on tenant_id; the primary key also serves the lookups by organization and
-- by organization + application.
create table if not exists readiness_severity_overrides (
    tenant_id        varchar(100) not null,
    application_code varchar(100) not null,
    step_id          varchar(100) not null,
    severity         varchar(10)  not null,
    updated_by       varchar(100),
    updated_at       timestamptz  not null default now(),
    constraint pk_readiness_severity_overrides primary key (tenant_id, application_code, step_id),
    constraint ck_readiness_severity_overrides_severity check (severity in ('BLOCK', 'WARN'))
);
