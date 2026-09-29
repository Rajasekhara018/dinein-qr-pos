-- =====================================================================
-- Records sensitive admin actions (staff created/updated, settings
-- changed) for accountability. Append-only: rows are never updated
-- or deleted by the application.
-- =====================================================================

CREATE TABLE audit_log (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    restaurant_id BIGINT NOT NULL,
    staff_user_id BIGINT,
    action VARCHAR(60) NOT NULL,
    entity_type VARCHAR(60) NOT NULL,
    entity_id BIGINT,
    previous_value VARCHAR(500),
    new_value VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_log_restaurant_created ON audit_log (restaurant_id, created_at DESC);
