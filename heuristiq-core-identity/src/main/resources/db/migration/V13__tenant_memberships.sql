-- A person's membership of an organization: one account for the whole platform, one membership per organization they work
-- in, told apart by (tenant_id, user_id). A membership is never deleted when withdrawn; it is marked REMOVED so history
-- stays readable. Existing accounts are given a membership in the default organization by the application on start.
create table if not exists tenant_memberships (
    id          varchar(255) primary key,
    tenant_id   varchar(255) not null,
    user_id     varchar(255) not null,
    status      varchar(20)  not null,
    employee_id varchar(64),
    added_by    varchar(100),
    joined_at   timestamp(6),
    updated_by  varchar(100),
    updated_at  timestamp(6)
);

create unique index if not exists uk_tenant_membership on tenant_memberships (tenant_id, user_id);

create index if not exists idx_tenant_memberships_user   on tenant_memberships (user_id);
create index if not exists idx_tenant_memberships_tenant on tenant_memberships (tenant_id, status);
