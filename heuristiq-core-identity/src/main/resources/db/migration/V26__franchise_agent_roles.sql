-- The roles an owner may give an agent in each application (set by an administrator), and the role an agent was given.
-- Portable SQL only (the migration is also exercised on H2 in tests).

create table if not exists org_franchise_agent_role (
    tenant_id        varchar(64)  not null,
    application_code varchar(100) not null,
    role_code        varchar(100) not null,
    created_at       timestamp(6) not null,
    primary key (tenant_id, application_code, role_code)
);

alter table org_franchise_agent add column if not exists role_code varchar(100);
