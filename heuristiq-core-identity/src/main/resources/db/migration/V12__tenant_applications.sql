-- Which registered applications (applications.code) an organization (tenants.id) may use. Applications belong to the
-- platform; this is the link. A subscription is never deleted when withdrawn; it is marked CANCELLED so history stays readable.
create table if not exists tenant_applications (
    id               varchar(255) primary key,
    tenant_id        varchar(255) not null,
    application_code varchar(100) not null,
    status           varchar(20)  not null,
    max_users        integer,
    valid_until      date,
    note             varchar(500),
    subscribed_by    varchar(100),
    subscribed_at    timestamp(6),
    updated_by       varchar(100),
    updated_at       timestamp(6)
);

create unique index if not exists uk_tenant_application on tenant_applications (tenant_id, application_code);

create index if not exists idx_tenant_applications_tenant on tenant_applications (tenant_id, status);

-- The organization everything belonged to before organizations were separated. Its id is the one the org structure already
-- stores as tenant_id ("default"), so nothing there needs moving. Applications are linked to it by the application on start.
insert into tenants (id, code, name, type, status, active, system_tenant, version, created_at)
select 'default', 'DEFAULT', 'Default organization', 'INTERNAL', 'ACTIVE', true, true, 0, current_timestamp
where not exists (select 1 from tenants where code = 'DEFAULT' or id = 'default');
