-- Roles import (docs/ORG_DEPARTMENT_IMPORT_DESIGN.md, "Roles and logins", step 4): an uploaded CSV of role rules is staged and validated
-- row by row (dry run); no rule is added until the batch is confirmed. Tables of their own like the department (V19) and ladders (V21)
-- imports. V19, V20 and V21 are never edited. Every read filters on tenant_id. Portable SQL only.

create table if not exists org_role_rule_import (
    id              varchar(64)  primary key,
    tenant_id       varchar(64)  not null,
    status          varchar(20)  not null,
    file_name       varchar(255),
    document_id     varchar(64),
    total_rows      integer      not null default 0,
    valid_rows      integer      not null default 0,
    error_rows      integer      not null default 0,
    created_count   integer      not null default 0,
    unchanged_count integer      not null default 0,
    message         varchar(500),
    created_by      varchar(100),
    created_at      timestamp(6) not null,
    applied_at      timestamp(6),
    version         bigint       not null default 0
);
create index if not exists idx_org_role_rule_import_tenant on org_role_rule_import (tenant_id, created_at);

-- One staged file row (one role rule). status: VALID / INVALID (after validation), APPLIED. A half-applied batch resumes from the rows that
-- are still VALID; a rule that already exists is simply counted as unchanged, so applying twice is safe.
create table if not exists org_role_rule_import_row (
    import_id       varchar(64)  not null,
    row_no          integer      not null,
    status          varchar(20)  not null,
    department_code varchar(100),
    ladder          varchar(200),
    position_name   varchar(200),
    role_code       varchar(100),
    errors          varchar(2000),
    primary key (import_id, row_no),
    constraint fk_org_role_rule_import_row_import foreign key (import_id) references org_role_rule_import (id)
);
create index if not exists idx_org_role_rule_import_row_status on org_role_rule_import_row (import_id, status, row_no);
