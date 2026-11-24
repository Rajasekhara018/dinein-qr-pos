-- "Undo the last publish" needs the structure as it was just before a draft went live. A snapshot is taken in the same
-- transaction as the publish and kept in these shadow tables (same columns as the live tables plus the draft they belong
-- to, no foreign keys). Rolling back restores the live tables from them and then drops them.
-- Portable SQL only (the migration is also exercised on H2 in tests).

create table if not exists org_change_set_snapshot (
    change_set_id  varchar(64)  primary key,
    tenant_id      varchar(64)  not null,
    include_people boolean      not null,
    created_at     timestamp(6) not null
);

create table if not exists org_snap_ladder (
    change_set_id varchar(64)  not null,
    id            varchar(64)  not null,
    tenant_id     varchar(64)  not null,
    name          varchar(200) not null,
    version       bigint       not null,
    created_at    timestamp(6) not null,
    primary key (change_set_id, id)
);

create table if not exists org_snap_position (
    change_set_id varchar(64)  not null,
    id            varchar(64)  not null,
    tenant_id     varchar(64)  not null,
    ladder_id     varchar(64)  not null,
    name          varchar(200) not null,
    rank_no       integer      not null,
    managerial    boolean      not null,
    primary key (change_set_id, id)
);

create table if not exists org_snap_unit (
    change_set_id    varchar(64)  not null,
    id               varchar(64)  not null,
    tenant_id        varchar(64)  not null,
    parent_id        varchar(64),
    code             varchar(100) not null,
    name             varchar(200) not null,
    unit_type        varchar(50),
    ladder_id        varchar(64),
    head_employee_id varchar(64),
    status           varchar(20)  not null,
    version          bigint       not null,
    created_at       timestamp(6) not null,
    updated_at       timestamp(6) not null,
    primary key (change_set_id, id)
);

create table if not exists org_snap_closure (
    change_set_id varchar(64) not null,
    ancestor_id   varchar(64) not null,
    descendant_id varchar(64) not null,
    depth         integer     not null,
    primary key (change_set_id, ancestor_id, descendant_id)
);

create table if not exists org_snap_assignment (
    change_set_id varchar(64) not null,
    id            varchar(64) not null,
    tenant_id     varchar(64) not null,
    employee_id   varchar(64) not null,
    unit_id       varchar(64) not null,
    position_id   varchar(64) not null,
    is_primary    boolean     not null,
    start_date    date        not null,
    end_date      date,
    primary key (change_set_id, id)
);

create table if not exists org_snap_reporting_line (
    change_set_id       varchar(64) not null,
    id                  varchar(64) not null,
    tenant_id           varchar(64) not null,
    employee_id         varchar(64) not null,
    manager_employee_id varchar(64) not null,
    line_type           varchar(20) not null,
    start_date          date        not null,
    end_date            date,
    primary key (change_set_id, id)
);
