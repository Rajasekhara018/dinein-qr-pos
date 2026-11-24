-- Bulk department import (docs/ORG_DEPARTMENT_IMPORT_DESIGN.md): an uploaded CSV of departments is staged and validated row by row
-- (dry run); no department is created or changed until the batch is confirmed. Same life cycle as the employee import (V8) but kept in
-- tables of its own so that import is not touched. Every read filters on tenant_id. Portable SQL only.

create table if not exists org_dept_import (
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
-- the "recent imports" list (newest first) and the rollback guard (anything applied since a time) both start from the organization.
create index if not exists idx_org_dept_import_tenant on org_dept_import (tenant_id, created_at);
create index if not exists idx_org_dept_import_applied on org_dept_import (tenant_id, status, applied_at);

-- One staged file row. status: VALID / INVALID (after validation), APPLIED (the department is created or updated).
-- A half-applied batch is resumed by looking at the rows that are still VALID.
create table if not exists org_dept_import_row (
    import_id       varchar(64)  not null,
    row_no          integer      not null,
    status          varchar(20)  not null,
    code            varchar(100),
    name            varchar(200),
    parent_code     varchar(100),
    unit_type       varchar(50),
    errors          varchar(2000),
    primary key (import_id, row_no),
    constraint fk_org_dept_import_row_import foreign key (import_id) references org_dept_import (id)
);
-- the rejected-rows list and the apply passes both read "this batch, this status, after this row".
create index if not exists idx_org_dept_import_row_status on org_dept_import_row (import_id, status, row_no);
