-- POS organization model: legal entities (the companies that issue invoices), their branches and the branch staff.
-- tenant_id is the UAM tenant (organization), a varchar(64) as in every other identity table; every query filters on it.

create table pos_legal_entity (
    id                          varchar(64)  primary key,
    tenant_id                   varchar(64)  not null,
    country                     char(2)      not null,
    legal_name                  varchar(200) not null,
    currency                    char(3)      not null,
    address_line1               varchar(200),
    address_line2               varchar(200),
    address_city                varchar(100),
    address_region              varchar(100),
    address_postal_code         varchar(20),
    address_country             char(2),
    invoice_prefix              varchar(10)  not null,
    financial_year_start_month  smallint     not null check (financial_year_start_month between 1 and 12),
    status                      varchar(16)  not null,
    version                     bigint       not null,
    updated_at                  timestamptz  not null,
    updated_by                  varchar(64)
);
create index idx_pos_legal_entity_tenant on pos_legal_entity (tenant_id);

create table pos_legal_entity_tax_id (
    legal_entity_id  varchar(64) not null references pos_legal_entity (id) on delete cascade,
    tenant_id        varchar(64) not null,
    type             varchar(16) not null,
    value            varchar(32) not null,
    primary key (legal_entity_id, type)
);

create table pos_branch (
    id                   varchar(64)  primary key,
    tenant_id            varchar(64)  not null,
    legal_entity_id      varchar(64)  not null references pos_legal_entity (id),
    code                 varchar(20)  not null,
    name                 varchar(120) not null,
    time_zone            varchar(64)  not null,
    locale               varchar(16)  not null,
    languages            varchar(64)  not null,
    phone                varchar(16)  not null,
    email                varchar(254),
    address_line1        varchar(200),
    address_line2        varchar(200),
    address_city         varchar(100),
    address_region       varchar(100),
    address_postal_code  varchar(20),
    address_country      char(2),
    status               varchar(16)  not null,
    version              bigint       not null,
    updated_at           timestamptz  not null,
    updated_by           varchar(64),
    constraint uk_pos_branch_tenant_code unique (tenant_id, code)
);
create index idx_pos_branch_tenant_entity on pos_branch (tenant_id, legal_entity_id);

create table pos_branch_staff (
    tenant_id    varchar(64) not null,
    branch_id    varchar(64) not null references pos_branch (id) on delete cascade,
    user_id      varchar(64) not null,
    assigned_by  varchar(64),
    assigned_at  timestamptz not null,
    primary key (branch_id, user_id)
);
create index idx_pos_branch_staff_user on pos_branch_staff (tenant_id, user_id);
