-- A franchise is a top-level unit (unit_type = 'FRANCHISE') with one owner login. It can take several applications;
-- each has its own fee and its own number of agent places (the franchise's total is their sum). An agent is placed in
-- the franchise unit and is tied to one application.
-- The owner is kept on the unit, not as its head: a head must be assigned to the unit and a person belongs to one
-- department only, so a head could never own a second franchise.
-- Portable SQL only (the migration is also exercised on H2 in tests).

alter table org_unit add column if not exists owner_user_id varchar(64);
create index if not exists idx_org_unit_owner on org_unit (tenant_id, owner_user_id);

create table if not exists org_franchise_application (
    id               varchar(64)   primary key,
    tenant_id        varchar(64)   not null,
    unit_id          varchar(64)   not null,
    application_code varchar(100)  not null,
    agent_limit      integer       not null,
    fee              decimal(14,2) not null,
    currency         varchar(3)    not null,
    version          bigint        not null default 0,
    created_at       timestamp(6)  not null,
    constraint uk_franchise_application unique (unit_id, application_code),
    constraint fk_franchise_application_unit foreign key (unit_id) references org_unit (id)
);
create index if not exists idx_franchise_application_tenant on org_franchise_application (tenant_id, application_code);

-- Which application each agent works for. A place is used only while the agent's placement in the unit is open.
create table if not exists org_franchise_agent (
    employee_id      varchar(64)  primary key,
    tenant_id        varchar(64)  not null,
    unit_id          varchar(64)  not null,
    application_code varchar(100) not null,
    created_at       timestamp(6) not null,
    constraint fk_franchise_agent_employee foreign key (employee_id) references org_employee (id),
    constraint fk_franchise_agent_unit foreign key (unit_id) references org_unit (id)
);
create index if not exists idx_franchise_agent_unit on org_franchise_agent (tenant_id, unit_id, application_code);
