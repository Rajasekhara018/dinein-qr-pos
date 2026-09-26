-- ---------------------------------------------------------------------
-- Notifications: in-app inbox, push subscriptions, delivery log
-- ---------------------------------------------------------------------

-- Contact details used for owner alerts (email) and future staff SMS.
ALTER TABLE staff_user
    ADD COLUMN email VARCHAR(120),
    ADD COLUMN phone VARCHAR(15);

-- One row per target: a role (every OWNER sees it) or a single staff user.
CREATE TABLE in_app_notification (
    id              BIGSERIAL PRIMARY KEY,
    audience        VARCHAR(20)  NOT NULL CHECK (audience IN ('STAFF_ROLE', 'STAFF_USER')),
    recipient       VARCHAR(64)  NOT NULL,
    event           VARCHAR(40)  NOT NULL,
    title           VARCHAR(120) NOT NULL,
    body            VARCHAR(500) NOT NULL,
    link            VARCHAR(300),
    severity        VARCHAR(10)  NOT NULL CHECK (severity IN ('INFO', 'HIGH')),
    related_order_id BIGINT      REFERENCES orders (id),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_in_app_notification_target ON in_app_notification (audience, recipient, created_at);

-- Read state is per staff user for both audiences, so a role-wide notification read by one owner stays unread for
-- the others. Absence of a row means unread.
CREATE TABLE notification_read (
    notification_id BIGINT       NOT NULL REFERENCES in_app_notification (id) ON DELETE CASCADE,
    staff_user_id   BIGINT       NOT NULL REFERENCES staff_user (id),
    read_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (notification_id, staff_user_id)
);
CREATE INDEX idx_notification_read_user ON notification_read (staff_user_id);

-- Device registration tokens (e.g. FCM) for staff users and guest sessions.
CREATE TABLE push_subscription (
    id              BIGSERIAL PRIMARY KEY,
    provider        VARCHAR(20)   NOT NULL,
    token           VARCHAR(1024) NOT NULL,
    owner_type      VARCHAR(20)   NOT NULL CHECK (owner_type IN ('STAFF_USER', 'GUEST_SESSION')),
    owner_id        VARCHAR(64)   NOT NULL,
    order_id        BIGINT        REFERENCES orders (id),
    platform        VARCHAR(120),
    is_active       BOOLEAN       NOT NULL DEFAULT true,
    last_used_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_push_subscription_token UNIQUE (provider, token)
);
CREATE INDEX idx_push_subscription_owner ON push_subscription (owner_type, owner_id) WHERE is_active;

-- Every delivery attempt; recipients are stored masked only.
CREATE TABLE notification_log (
    id                  BIGSERIAL PRIMARY KEY,
    channel             VARCHAR(10)  NOT NULL CHECK (channel IN ('EMAIL', 'SMS', 'PUSH', 'IN_APP')),
    provider            VARCHAR(20)  NOT NULL,
    template            VARCHAR(40)  NOT NULL,
    recipient_masked    VARCHAR(120),
    status              VARCHAR(10)  NOT NULL CHECK (status IN ('SENT', 'FAILED', 'SKIPPED')),
    error               VARCHAR(500),
    related_order_id    BIGINT,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_notification_log_created ON notification_log (created_at);
CREATE INDEX idx_notification_log_order ON notification_log (related_order_id);
