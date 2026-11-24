-- Franchise plans: the agent places and fee suggested when a franchise is opened for an application, optionally for a
-- country, a state and a city (a blank country is the application's general plan; a blank state or city covers the wider
-- area). One organization's plans are invisible to another's (tenant_id leads every index and the unique key).
-- Portable SQL only (the migration is also exercised on H2 in tests).

create table if not exists org_franchise_plan (
    id               varchar(64)   primary key,
    tenant_id        varchar(64)   not null,
    application_code varchar(100)  not null,
    country_code     varchar(2)    not null default '',
    country_name     varchar(100)  not null default '',
    state_code       varchar(10)   not null default '',
    state_name       varchar(100)  not null default '',
    city             varchar(100)  not null default '',
    agent_limit      integer       not null,
    fee              decimal(14,2) not null,
    currency         varchar(3)    not null,
    version          bigint        not null default 0,
    created_at       timestamp(6)  not null,
    updated_at       timestamp(6)  not null,
    constraint uk_franchise_plan unique (tenant_id, application_code, country_code, state_code, city)
);
create index if not exists idx_franchise_plan_tenant on org_franchise_plan (tenant_id, application_code);
