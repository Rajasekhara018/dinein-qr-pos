-- Redacted request/response headers and bodies of an audit event, kept apart from audit_events so event lists
-- stay small. Keyed by the event's event_id; expire_at equals the event's, so the purge removes both together.
-- Bodies may still hold personal data (names, addresses): credentials, card/account numbers are removed and
-- phones, emails and UPI addresses masked before insert.
CREATE TABLE audit_event_payloads (
    event_id                varchar(64) PRIMARY KEY,
    request_headers         jsonb,
    response_headers        jsonb,
    request_body            text,
    response_body           text,
    request_body_truncated  boolean     NOT NULL DEFAULT false,
    response_body_truncated boolean     NOT NULL DEFAULT false,
    request_body_size       bigint,
    response_body_size      bigint,
    body_content_type       varchar(100),
    expire_at               timestamptz NOT NULL
);

CREATE INDEX idx_audit_payload_expire_at ON audit_event_payloads (expire_at);
