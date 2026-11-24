-- The roles a franchise owner gets, for each application the franchise takes (set by an administrator): all of them, automatically.
-- Same shape as org_franchise_agent_role, which lists the roles an owner may choose from for an agent.
-- Portable SQL only (the migration is also exercised on H2 in tests).

create table if not exists org_franchise_owner_role (
    tenant_id        varchar(64)  not null,
    application_code varchar(100) not null,
    role_code        varchar(100) not null,
    created_at       timestamp(6) not null,
    primary key (tenant_id, application_code, role_code)
);
