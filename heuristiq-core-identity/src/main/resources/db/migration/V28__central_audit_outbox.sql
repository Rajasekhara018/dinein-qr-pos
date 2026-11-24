-- Outbox of UAM domain audit events waiting to be pushed to Rider_AuditService. A row is written in the same
-- transaction as the business action (when there is one) and removed only after the audit service accepted it.
-- A drainer claims rows with a short lease (locked_until + lock_token) so several UAM instances never send the same row.
-- Portable SQL only (the migration is also exercised on H2 in tests).

create table if not exists central_audit_outbox (
    event_id        varchar(64)  primary key,
    tenant_id       varchar(64),
    payload         text         not null,
    attempts        integer      not null default 0,
    created_at      timestamp(6) not null,
    next_attempt_at timestamp(6) not null,
    locked_until    timestamp(6),
    lock_token      varchar(64)
);
create index if not exists idx_central_audit_outbox_due on central_audit_outbox (next_attempt_at);
create index if not exists idx_central_audit_outbox_created on central_audit_outbox (created_at);
