-- Which users, and which roles, may sign in to which registered application (applications.code).
-- A grant is never deleted when withdrawn; it is marked inactive so the history stays readable.
create table if not exists application_access (
    id               varchar(255) primary key,
    application_code varchar(100) not null,
    subject_type     varchar(20)  not null,
    subject_id       varchar(100) not null,
    active           boolean      not null default true,
    note             varchar(500),
    granted_by       varchar(100),
    granted_at       timestamp(6),
    revoked_by       varchar(100),
    revoked_at       timestamp(6),
    constraint uk_application_access_subject unique (application_code, subject_type, subject_id)
);

create index if not exists idx_application_access_subject     on application_access (subject_type, subject_id, active);
create index if not exists idx_application_access_application on application_access (application_code);
