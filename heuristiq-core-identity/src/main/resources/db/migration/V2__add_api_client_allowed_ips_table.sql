-- V1 already defines this table alongside its sibling api_client_* collection tables, but it is
-- missing on environments where Flyway's history shows V1 as already applied (Flyway migrations
-- are immutable once recorded, so V1 never re-runs). Recreated here idempotently so this migration
-- is a no-op on any environment that already has the table.
create table if not exists api_client_allowed_ips (api_client_id varchar(255) not null, ip_address varchar(255));

do $$
begin
    if not exists (
        select 1 from pg_constraint where conname = 'fk_api_client_allowed_ips_client'
    ) then
        alter table api_client_allowed_ips
            add constraint fk_api_client_allowed_ips_client
            foreign key (api_client_id) references api_clients (id);
    end if;
end $$;
