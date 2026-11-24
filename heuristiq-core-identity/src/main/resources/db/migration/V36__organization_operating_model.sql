-- An organization's operating-model changes (docs/OPERATING_MODEL_DESIGN.md, section 3): from each day the organization runs
-- its sales itself (DIRECT) or through franchises (FRANCHISE). One row per organization and start day; a change in force is
-- never updated or deleted, only changes that have not started are replaced. Kept apart from tenants.settings so an
-- organization edit can never overwrite it. Every read filters on tenant_id; the primary key serves them.
create table if not exists organization_operating_model_changes (
    tenant_id      varchar(100) not null,
    effective_from date         not null,
    model          varchar(20)  not null,
    created_by     varchar(100),
    created_at     timestamptz  not null default now(),
    constraint pk_organization_operating_model_changes primary key (tenant_id, effective_from),
    constraint ck_organization_operating_model_changes_model check (model in ('DIRECT', 'FRANCHISE'))
);
