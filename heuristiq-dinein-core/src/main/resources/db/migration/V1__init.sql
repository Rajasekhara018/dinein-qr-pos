-- =====================================================================
-- DineIn QR self-ordering: initial schema
-- Conventions: BIGSERIAL ids, TIMESTAMPTZ timestamps, NUMERIC(10,2) money.
-- =====================================================================

-- ---------------------------------------------------------------------
-- Images (bytes live only here; menu tables reference by id)
-- ---------------------------------------------------------------------
CREATE TABLE image (
    id              BIGSERIAL PRIMARY KEY,
    content_type    VARCHAR(50)  NOT NULL CHECK (content_type IN ('image/jpeg', 'image/png', 'image/webp')),
    data            BYTEA        NOT NULL,
    thumbnail       BYTEA        NOT NULL,
    size_bytes      INT          NOT NULL,
    width           INT,
    height          INT,
    sha256          CHAR(64)     NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_image_sha256 ON image (sha256);

-- ---------------------------------------------------------------------
-- Menu
-- ---------------------------------------------------------------------
CREATE TABLE category (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(80)  NOT NULL,
    description     VARCHAR(300),
    image_id        BIGINT       REFERENCES image (id),
    display_order   INT          NOT NULL DEFAULT 0,
    is_active       BOOLEAN      NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_category_name ON category (lower(name));

CREATE TABLE item (
    id              BIGSERIAL PRIMARY KEY,
    category_id     BIGINT       NOT NULL REFERENCES category (id),
    name            VARCHAR(120) NOT NULL,
    description     VARCHAR(500),
    image_id        BIGINT       REFERENCES image (id),
    base_price      NUMERIC(10,2) CHECK (base_price IS NULL OR base_price >= 0),
    food_type       VARCHAR(10)  NOT NULL CHECK (food_type IN ('VEG', 'NON_VEG', 'EGG')),
    gst_percent     NUMERIC(4,2) NOT NULL DEFAULT 5.00 CHECK (gst_percent >= 0 AND gst_percent <= 28),
    is_available    BOOLEAN      NOT NULL DEFAULT true,
    is_active       BOOLEAN      NOT NULL DEFAULT true,
    display_order   INT          NOT NULL DEFAULT 0,
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_item_category_name ON item (category_id, lower(name));
CREATE INDEX idx_item_category_active ON item (category_id, is_active);

CREATE TABLE item_variant (
    id              BIGSERIAL PRIMARY KEY,
    item_id         BIGINT        NOT NULL REFERENCES item (id),
    name            VARCHAR(50)   NOT NULL,
    price           NUMERIC(10,2) NOT NULL CHECK (price > 0),
    is_default      BOOLEAN       NOT NULL DEFAULT false,
    display_order   INT           NOT NULL DEFAULT 0,
    is_active       BOOLEAN       NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_item_variant_item ON item_variant (item_id);

CREATE TABLE addon (
    id              BIGSERIAL PRIMARY KEY,
    item_id         BIGINT        NOT NULL REFERENCES item (id),
    name            VARCHAR(50)   NOT NULL,
    price           NUMERIC(10,2) NOT NULL CHECK (price >= 0),
    is_active       BOOLEAN       NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_addon_item ON addon (item_id);

-- ---------------------------------------------------------------------
-- Tables & QR
-- ---------------------------------------------------------------------
CREATE TABLE dining_table (
    id              BIGSERIAL PRIMARY KEY,
    label           VARCHAR(20)  NOT NULL UNIQUE,
    qr_token        VARCHAR(64)  NOT NULL UNIQUE,
    is_active       BOOLEAN      NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------
-- Orders
-- ---------------------------------------------------------------------
CREATE TABLE daily_order_counter (
    business_date   DATE PRIMARY KEY,
    last_value      INT  NOT NULL
);

CREATE TABLE orders (
    id                  BIGSERIAL PRIMARY KEY,
    order_number        VARCHAR(20)   NOT NULL UNIQUE,
    display_token       INT           NOT NULL,
    table_id            BIGINT        REFERENCES dining_table (id),
    guest_session_id    VARCHAR(64)   NOT NULL,
    customer_name       VARCHAR(60),
    customer_phone      VARCHAR(15),
    notes               VARCHAR(300),
    status              VARCHAR(20)   NOT NULL CHECK (status IN ('PENDING_PAYMENT', 'CONFIRMED', 'PREPARING', 'READY',
                                                                 'COMPLETED', 'EXPIRED', 'PAYMENT_FAILED', 'CANCELLED')),
    subtotal            NUMERIC(10,2) NOT NULL CHECK (subtotal >= 0),
    tax_total           NUMERIC(10,2) NOT NULL CHECK (tax_total >= 0),
    grand_total         NUMERIC(10,2) NOT NULL CHECK (grand_total > 0),
    prices_include_gst  BOOLEAN       NOT NULL DEFAULT false,
    idempotency_key    VARCHAR(64)   NOT NULL UNIQUE,
    payment_flagged     BOOLEAN       NOT NULL DEFAULT false,
    flag_reason         VARCHAR(300),
    cancel_reason       VARCHAR(300),
    placed_at           TIMESTAMPTZ   NOT NULL,
    paid_at             TIMESTAMPTZ,
    preparing_at        TIMESTAMPTZ,
    ready_at            TIMESTAMPTZ,
    completed_at        TIMESTAMPTZ,
    cancelled_at        TIMESTAMPTZ,
    version             BIGINT        NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_orders_status_placed ON orders (status, placed_at);
CREATE INDEX idx_orders_guest_session ON orders (guest_session_id);

CREATE TABLE order_item (
    id              BIGSERIAL PRIMARY KEY,
    order_id        BIGINT        NOT NULL REFERENCES orders (id),
    item_id         BIGINT        REFERENCES item (id),
    variant_id      BIGINT        REFERENCES item_variant (id),
    item_name       VARCHAR(120)  NOT NULL,
    variant_name    VARCHAR(50),
    food_type       VARCHAR(10),
    unit_price      NUMERIC(10,2) NOT NULL,
    quantity        INT           NOT NULL CHECK (quantity BETWEEN 1 AND 50),
    gst_percent     NUMERIC(4,2)  NOT NULL,
    line_total      NUMERIC(10,2) NOT NULL,
    tax_amount      NUMERIC(10,2) NOT NULL,
    notes           VARCHAR(200),
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_order_item_order ON order_item (order_id);

CREATE TABLE order_item_addon (
    id              BIGSERIAL PRIMARY KEY,
    order_item_id   BIGINT        NOT NULL REFERENCES order_item (id),
    addon_id        BIGINT        REFERENCES addon (id),
    addon_name      VARCHAR(50)   NOT NULL,
    price           NUMERIC(10,2) NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_order_item_addon_item ON order_item_addon (order_item_id);

-- ---------------------------------------------------------------------
-- Payments (provider-agnostic: RAZORPAY, PAYU, PINELABS, ...)
-- ---------------------------------------------------------------------
CREATE TABLE payment (
    id                    BIGSERIAL PRIMARY KEY,
    order_id              BIGINT        NOT NULL REFERENCES orders (id),
    provider              VARCHAR(20)   NOT NULL,
    provider_order_id     VARCHAR(64)   NOT NULL,   -- Razorpay order id / PayU txnid / Pine Labs order id
    provider_payment_id   VARCHAR(64),              -- Razorpay payment id / PayU mihpayid
    amount_paise          BIGINT        NOT NULL CHECK (amount_paise > 0),
    currency              CHAR(3)       NOT NULL DEFAULT 'INR',
    status                VARCHAR(20)   NOT NULL CHECK (status IN ('CREATED', 'AUTHORIZED', 'CAPTURED', 'FAILED', 'REFUNDED')),
    method                VARCHAR(20),
    failure_reason        VARCHAR(300),
    captured_amount_paise BIGINT,
    provider_refund_id    VARCHAR(64),
    refund_status         VARCHAR(20)   CHECK (refund_status IS NULL OR refund_status IN ('PENDING', 'PROCESSED', 'FAILED')),
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_payment_provider_order UNIQUE (provider, provider_order_id),
    CONSTRAINT uq_payment_provider_payment UNIQUE (provider, provider_payment_id)
);
CREATE INDEX idx_payment_order ON payment (order_id);
-- (provider, provider_order_id) is indexed by its UNIQUE constraint.

CREATE TABLE payment_event (
    id                  BIGSERIAL PRIMARY KEY,
    provider            VARCHAR(20)  NOT NULL,
    provider_event_id   VARCHAR(100),
    event_type          VARCHAR(50),
    payload             JSONB        NOT NULL,
    signature_valid     BOOLEAN      NOT NULL,
    processed_at        TIMESTAMPTZ,
    processing_error    VARCHAR(500),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_payment_event_provider_event UNIQUE (provider, provider_event_id)
);

-- ---------------------------------------------------------------------
-- Staff, sessions & settings
-- ---------------------------------------------------------------------
CREATE TABLE staff_user (
    id                      BIGSERIAL PRIMARY KEY,
    username                VARCHAR(50)  NOT NULL UNIQUE,
    password_hash           VARCHAR(100) NOT NULL,
    pin_hash                VARCHAR(100),
    display_name            VARCHAR(80),
    role                    VARCHAR(20)  NOT NULL CHECK (role IN ('OWNER', 'MANAGER', 'KITCHEN')),
    is_active               BOOLEAN      NOT NULL DEFAULT true,
    must_change_password    BOOLEAN      NOT NULL DEFAULT false,
    failed_login_attempts   INT          NOT NULL DEFAULT 0,
    locked_until            TIMESTAMPTZ,
    last_login_at           TIMESTAMPTZ,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_staff_username_ci ON staff_user (lower(username));

CREATE TABLE refresh_token (
    id              BIGSERIAL PRIMARY KEY,
    staff_user_id   BIGINT       NOT NULL REFERENCES staff_user (id),
    token_hash      CHAR(64)     NOT NULL UNIQUE,
    family_id       VARCHAR(36)  NOT NULL,
    expires_at      TIMESTAMPTZ  NOT NULL,
    revoked_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_refresh_token_family ON refresh_token (family_id);

CREATE TABLE device_token (
    id              BIGSERIAL PRIMARY KEY,
    staff_user_id   BIGINT       NOT NULL REFERENCES staff_user (id),
    device_name     VARCHAR(60),
    token_hash      CHAR(64)     NOT NULL UNIQUE,
    expires_at      TIMESTAMPTZ  NOT NULL,
    revoked_at      TIMESTAMPTZ,
    last_seen_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE restaurant_settings (
    id                      BIGINT       PRIMARY KEY CHECK (id = 1),
    name                    VARCHAR(100) NOT NULL,
    address                 VARCHAR(300),
    phone                   VARCHAR(15),
    gstin                   VARCHAR(15),
    fssai_no                VARCHAR(20),
    logo_image_id           BIGINT       REFERENCES image (id),
    is_accepting_orders     BOOLEAN      NOT NULL DEFAULT true,
    prices_include_gst      BOOLEAN      NOT NULL DEFAULT false,
    opening_time            TIME,
    closing_time            TIME,
    currency                CHAR(3)      NOT NULL DEFAULT 'INR',
    brand_color             VARCHAR(7)   NOT NULL DEFAULT '#C2410C',
    kitchen_warn_minutes    INT          NOT NULL DEFAULT 10,
    kitchen_alert_minutes   INT          NOT NULL DEFAULT 20,
    ready_auto_hide_minutes INT          NOT NULL DEFAULT 15,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT now()
);
