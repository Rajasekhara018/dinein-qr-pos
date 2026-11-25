package com.heuristiq.documents.centralaudit;

/** Where an {@link OutboxEntry} is in its life. */
public enum OutboxStatus {
    PENDING,
    SENT,
    /** Gave up after the configured number of tries; kept until it expires, for inspection. */
    DEAD
}
