-- An organization's legal profile: legal entity, registration, tax ids and addresses (the web app's "Legal profile" form,
-- shared with the franchise legal profile). The organization's legal name is NOT stored here: it stays in tenants.legal_name,
-- its one owner. The organization is the tenant, so tenant_id is both the owner id and the organization filter; every read
-- and write filters on it. The PUT replaces the whole profile under the optimistic-lock version.
create table if not exists organization_legal_profile (
    tenant_id            varchar(100) not null,
    entity_type          varchar(30),
    trading_name         varchar(200),
    registration_number  varchar(50),
    registration_country varchar(2),
    tax_registered       boolean      not null default false,
    version              bigint       not null,
    updated_by           varchar(100),
    updated_at           timestamptz  not null default now(),
    constraint pk_organization_legal_profile primary key (tenant_id),
    constraint ck_organization_legal_profile_entity_type check (entity_type is null or entity_type in
        ('INDIVIDUAL', 'SOLE_PROPRIETOR', 'PARTNERSHIP', 'LLP', 'PRIVATE_COMPANY', 'PUBLIC_COMPANY', 'NON_PROFIT', 'GOVERNMENT', 'OTHER'))
);

-- Each tax type at most once per organization.
create table if not exists organization_tax_id (
    tenant_id varchar(100) not null,
    tax_type  varchar(20)  not null,
    tax_value varchar(40)  not null,
    constraint pk_organization_tax_id primary key (tenant_id, tax_type),
    constraint fk_organization_tax_id_profile foreign key (tenant_id) references organization_legal_profile (tenant_id) on delete cascade
);

-- For later look-ups of a tax id (for example "which organization holds this GSTIN").
create index if not exists idx_organization_tax_id_value on organization_tax_id (tenant_id, tax_type, tax_value);

-- The registered and the operating address, each at most once per organization.
create table if not exists organization_address (
    tenant_id   varchar(100) not null,
    kind        varchar(20)  not null,
    line1       varchar(200) not null,
    line2       varchar(200),
    city        varchar(100) not null,
    region      varchar(100),
    postal_code varchar(12),
    country     varchar(2)   not null,
    constraint pk_organization_address primary key (tenant_id, kind),
    constraint ck_organization_address_kind check (kind in ('REGISTERED', 'OPERATING')),
    constraint fk_organization_address_profile foreign key (tenant_id) references organization_legal_profile (tenant_id) on delete cascade
);
