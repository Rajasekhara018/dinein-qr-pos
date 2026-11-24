-- Role-mapping rules: which UAM role an employee should get from where they sit in the organization.
-- A rule matches an employee when its department (if set) is the employee's department or one above it, and its
-- position (if set) is the employee's position. All matching rules apply. Portable SQL only.

create table if not exists org_role_rule (
    id          varchar(64)  primary key,
    tenant_id   varchar(64)  not null,
    unit_id     varchar(64),
    position_id varchar(64),
    role_code   varchar(100) not null,
    active      boolean      not null default true,
    created_by  varchar(100),
    created_at  timestamp(6) not null,
    constraint fk_org_role_rule_unit foreign key (unit_id) references org_unit (id),
    constraint fk_org_role_rule_position foreign key (position_id) references org_position (id)
);
create index if not exists idx_org_role_rule_tenant on org_role_rule (tenant_id, active);
