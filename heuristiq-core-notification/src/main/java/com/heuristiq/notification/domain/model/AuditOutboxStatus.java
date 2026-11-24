package com.railcab.nms.domain.model;

/** Where an {@link AuditOutboxEntry} is in its life. */
public enum AuditOutboxStatus {
    PENDING,
    SENT,
    /** Gave up after the configured number of tries; kept until it expires, for inspection. */
    DEAD
}
