-- Bulk employee import: an uploaded file is staged and validated row by row (dry run); nothing touches
-- employees, assignments or reporting lines until the batch is confirmed. Portable SQL only.

create table if not exists org_import (
    id              varchar(64)  primary key,
    tenant_id       varchar(64)  not null,
    status          varchar(20)  not null,
    file_name       varchar(255),
    document_id     varchar(64),
    total_rows      integer      not null default 0,
    valid_rows      integer      not null default 0,
    error_rows      integer      not null default 0,
    created_count   integer      not null default 0,
    updated_count   integer      not null default 0,
    message         varchar(500),
    created_by      varchar(100),
    created_at      timestamp(6) not null,
    applied_at      timestamp(6),
    version         bigint       not null default 0
);
create index if not exists idx_org_import_tenant on org_import (tenant_id, created_at);

-- One staged file row. status: VALID / INVALID (after validation), PLACED (employee + unit + position applied),
-- APPLIED (reporting line applied too). Resuming a half-applied batch just continues from the unfinished rows.
create table if not exists org_import_row (
    import_id       varchar(64)  not null,
    row_no          integer      not null,
    status          varchar(20)  not null,
    employee_code   varchar(100),
    full_name       varchar(200),
    email           varchar(200),
    phone           varchar(50),
    department_code varchar(100),
    position_name   varchar(200),
    manager_code    varchar(100),
    joined_on       varchar(20),
    errors          varchar(2000),
    primary key (import_id, row_no),
    constraint fk_org_import_row_import foreign key (import_id) references org_import (id)
);
create index if not exists idx_org_import_row_status on org_import_row (import_id, status, row_no);
