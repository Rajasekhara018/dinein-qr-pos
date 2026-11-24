CREATE TABLE nms_broadcasts (
    id VARCHAR(255) PRIMARY KEY,
    title VARCHAR(255),
    event VARCHAR(255),
    segment VARCHAR(255),
    scheduled_at TIMESTAMP,
    status VARCHAR(255),
    recipient_count INTEGER,
    created_by VARCHAR(255),
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    sent_at TIMESTAMP
);

CREATE TABLE nms_device_tokens (
    id VARCHAR(255) PRIMARY KEY,
    user_id VARCHAR(255),
    token VARCHAR(500),
    platform VARCHAR(255),
    created_at TIMESTAMP
);

CREATE TABLE nms_in_app_notifications (
    id VARCHAR(255) PRIMARY KEY,
    user_id VARCHAR(255),
    title VARCHAR(255),
    message VARCHAR(4000),
    read BOOLEAN NOT NULL,
    created_at TIMESTAMP
);

CREATE TABLE nms_event_definitions (
    id VARCHAR(255) PRIMARY KEY,
    event_value VARCHAR(255),
    label VARCHAR(255),
    active BOOLEAN NOT NULL,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE nms_logs (
    id VARCHAR(255) PRIMARY KEY,
    message_id VARCHAR(255),
    correlation_id VARCHAR(255),
    event VARCHAR(255),
    channel VARCHAR(255),
    provider_name VARCHAR(255),
    provider_message_id VARCHAR(255),
    status VARCHAR(255),
    recipient VARCHAR(255),
    success BOOLEAN NOT NULL,
    retry_count INTEGER NOT NULL,
    error_message VARCHAR(2000),
    rendered_body VARCHAR(4000),
    sent_at TIMESTAMP
);

CREATE TABLE nms_messages (
    id VARCHAR(255) PRIMARY KEY,
    message_id VARCHAR(255),
    correlation_id VARCHAR(255),
    event VARCHAR(255),
    status VARCHAR(255),
    error_message VARCHAR(2000),
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    completed_at TIMESTAMP
);

CREATE TABLE nms_templates (
    id VARCHAR(255) PRIMARY KEY,
    event VARCHAR(255),
    channel VARCHAR(255),
    name VARCHAR(255),
    subject VARCHAR(255),
    body VARCHAR(4000),
    external_template_id VARCHAR(255),
    active BOOLEAN NOT NULL,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE nms_broadcast_variables (broadcast_id VARCHAR(255) NOT NULL REFERENCES nms_broadcasts(id), variable_key VARCHAR(255) NOT NULL, variable_value VARCHAR(255), PRIMARY KEY (broadcast_id, variable_key));
CREATE TABLE nms_broadcast_channels (broadcast_id VARCHAR(255) NOT NULL REFERENCES nms_broadcasts(id), idx INTEGER NOT NULL, channel VARCHAR(255), PRIMARY KEY (broadcast_id, idx));
CREATE TABLE nms_event_definition_attributes (event_definition_id VARCHAR(255) NOT NULL REFERENCES nms_event_definitions(id), idx INTEGER NOT NULL, attribute VARCHAR(255), PRIMARY KEY (event_definition_id, idx));
CREATE UNIQUE INDEX uk_nms_message_message_id ON nms_messages(message_id);
CREATE UNIQUE INDEX uk_nms_template_event_channel ON nms_templates(event, channel);
CREATE UNIQUE INDEX uk_nms_event_definition_value ON nms_event_definitions(event_value);
CREATE INDEX idx_nms_device_token_user_id ON nms_device_tokens(user_id);
CREATE INDEX idx_nms_device_token_token ON nms_device_tokens(token);
CREATE INDEX idx_nms_in_app_user_created ON nms_in_app_notifications(user_id, created_at);
CREATE INDEX idx_nms_log_message_id ON nms_logs(message_id);
CREATE INDEX idx_nms_log_sent_at ON nms_logs(sent_at);
CREATE INDEX idx_nms_message_completed_at ON nms_messages(completed_at);
CREATE INDEX idx_nms_broadcast_status_scheduled ON nms_broadcasts(status, scheduled_at);
