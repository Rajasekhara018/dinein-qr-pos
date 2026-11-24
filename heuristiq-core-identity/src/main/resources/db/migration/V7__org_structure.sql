-- Organization structure (departments, per-department position ladders, employees, assignments,
-- reporting lines) plus the draft change-set tables used by the preview/confirm flow.
-- tenant_id is the UAM tenant (organization); it leads every index and unique constraint so the
-- same tables serve many organizations later without a schema change.
-- Portable SQL only (the migration is also exercised on H2 in tests).

create table if not exists org_ladder (
    id         varchar(64)  primary key,
    tenant_id  varchar(64)  not null,
    name       varchar(200) not null,
    version    bigint       not null default 0,
    created_at timestamp(6) not null,
    constraint uk_org_ladder_name unique (tenant_id, name)
);

create table if not exists org_position (
    id         varchar(64)  primary key,
    tenant_id  varchar(64)  not null,
    ladder_id  varchar(64)  not null,
    name       varchar(200) not null,
    rank_no    integer      not null,
    managerial boolean      not null default false,
    constraint uk_org_position_rank unique (ladder_id, rank_no),
    constraint uk_org_position_name unique (ladder_id, name),
    constraint fk_org_position_ladder foreign key (ladder_id) references org_ladder (id)
);

create table if not exists org_unit (
    id               varchar(64)  primary key,
    tenant_id        varchar(64)  not null,
    parent_id        varchar(64),
    code             varchar(100) not null,
    name             varchar(200) not null,
    unit_type        varchar(50),
    ladder_id        varchar(64),
    head_employee_id varchar(64),
    status           varchar(20)  not null default 'ACTIVE',
    version          bigint       not null default 0,
    created_at       timestamp(6) not null,
    updated_at       timestamp(6) not null,
    constraint uk_org_unit_code unique (tenant_id, code),
    constraint fk_org_unit_parent foreign key (parent_id) references org_unit (id),
    constraint fk_org_unit_ladder foreign key (ladder_id) references org_ladder (id)
);
create index if not exists idx_org_unit_parent on org_unit (tenant_id, parent_id);

-- Every (ancestor, descendant) pair, including (x, x) at depth 0: "everyone under me" is one indexed join.
create table if not exists org_unit_closure (
    ancestor_id   varchar(64) not null,
    descendant_id varchar(64) not null,
    depth         integer     not null,
    primary key (ancestor_id, descendant_id),
    constraint fk_org_closure_ancestor foreign key (ancestor_id) references org_unit (id),
    constraint fk_org_closure_descendant foreign key (descendant_id) references org_unit (id)
);
create index if not exists idx_org_closure_descendant on org_unit_closure (descendant_id, ancestor_id);

-- An employee is not necessarily a login: user_id is filled when a UAM user is linked.
create table if not exists org_employee (
    id            varchar(64)  primary key,
    tenant_id     varchar(64)  not null,
    employee_code varchar(100) not null,
    full_name     varchar(200) not null,
    email         varchar(200),
    phone         varchar(50),
    status        varchar(20)  not null default 'ACTIVE',
    user_id       varchar(64),
    joined_on     date,
    version       bigint       not null default 0,
    created_at    timestamp(6) not null,
    constraint uk_org_employee_code unique (tenant_id, employee_code)
);
create index if not exists idx_org_employee_user on org_employee (tenant_id, user_id);
create index if not exists idx_org_employee_name on org_employee (tenant_id, full_name);

-- Dated rows so history is kept and a person can later hold more than one assignment.
create table if not exists org_assignment (
    id          varchar(64)  primary key,
    tenant_id   varchar(64)  not null,
    employee_id varchar(64)  not null,
    unit_id     varchar(64)  not null,
    position_id varchar(64)  not null,
    is_primary  boolean      not null default true,
    start_date  date         not null,
    end_date    date,
    constraint fk_org_assignment_employee foreign key (employee_id) references org_employee (id),
    constraint fk_org_assignment_unit foreign key (unit_id) references org_unit (id),
    constraint fk_org_assignment_position foreign key (position_id) references org_position (id)
);
create index if not exists idx_org_assignment_unit on org_assignment (tenant_id, unit_id, end_date);
create index if not exists idx_org_assignment_employee on org_assignment (tenant_id, employee_id, end_date);

-- Separate from the assignment so a second manager is just another row.
create table if not exists org_reporting_line (
    id                  varchar(64) primary key,
    tenant_id           varchar(64) not null,
    employee_id         varchar(64) not null,
    manager_employee_id varchar(64) not null,
    line_type           varchar(20) not null default 'PRIMARY',
    start_date          date        not null,
    end_date            date,
    constraint fk_org_line_employee foreign key (employee_id) references org_employee (id),
    constraint fk_org_line_manager foreign key (manager_employee_id) references org_employee (id)
);
create index if not exists idx_org_line_employee on org_reporting_line (tenant_id, employee_id, end_date);
create index if not exists idx_org_line_manager on org_reporting_line (tenant_id, manager_employee_id, end_date);

-- A draft is an ordered list of operations applied to the live tables only on publish.
create table if not exists org_change_set (
    id           varchar(64)  primary key,
    tenant_id    varchar(64)  not null,
    status       varchar(20)  not null,
    note         varchar(500),
    created_by   varchar(100),
    created_at   timestamp(6) not null,
    published_by varchar(100),
    published_at timestamp(6),
    version      bigint       not null default 0
);
create index if not exists idx_org_change_set_tenant on org_change_set (tenant_id, status);

create table if not exists org_change (
    id            varchar(64)  primary key,
    change_set_id varchar(64)  not null,
    seq           integer      not null,
    op_type       varchar(40)  not null,
    payload       text         not null,
    constraint uk_org_change_seq unique (change_set_id, seq),
    constraint fk_org_change_set foreign key (change_set_id) references org_change_set (id)
);
