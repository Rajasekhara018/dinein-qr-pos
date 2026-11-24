-- "Everything by CSV" (docs/ORG_DEPARTMENT_IMPORT_DESIGN.md): the ladders & positions import (a third import kind, tables of its own like the
-- department import in V19) and the is_head column of the employee import (V8). V19 and V20 are applied in production and are never edited,
-- so everything new is here. Every read filters on tenant_id. Portable SQL only; the alters are safe to run again.

create table if not exists org_ladder_import (
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
    ladders_created integer      not null default 0,
    message         varchar(500),
    created_by      varchar(100),
    created_at      timestamp(6) not null,
    applied_at      timestamp(6),
    version         bigint       not null default 0
);
-- the "recent imports" list (newest first) and the rollback guard (anything applied since a time) both start from the organization.
create index if not exists idx_org_ladder_import_tenant on org_ladder_import (tenant_id, created_at);
create index if not exists idx_org_ladder_import_applied on org_ladder_import (tenant_id, status, applied_at);

-- One staged file row (one job title of one ladder). status: VALID / INVALID (after validation), APPLIED. rank_text and managerial hold the
-- cells as typed; the validator has already checked them. A half-applied batch resumes from the rows that are still VALID.
create table if not exists org_ladder_import_row (
    import_id       varchar(64)  not null,
    row_no          integer      not null,
    status          varchar(20)  not null,
    ladder          varchar(200),
    position_name   varchar(200),
    rank_text       varchar(20),
    managerial      varchar(10),
    errors          varchar(2000),
    primary key (import_id, row_no),
    constraint fk_org_ladder_import_row_import foreign key (import_id) references org_ladder_import (id)
);
create index if not exists idx_org_ladder_import_row_status on org_ladder_import_row (import_id, status, row_no);

-- Employee import: the is_head cell of a row and how many department heads a batch has set.
alter table org_import add column if not exists heads_set integer not null default 0;
alter table org_import_row add column if not exists is_head varchar(10);
