package com.railcab.nms.adapters.out.persistence.jpa;

/** Fixed column sizes of the JPA persistence adapter, shared by the entity mapping and the code that fits values to it. */
final class JpaPersistenceConstants {

    /** Length of {@code AuditOutboxEntity.lastError}; longer errors are cut to fit. */
    static final int AUDIT_OUTBOX_ERROR_COLUMN_LENGTH = 500;

    private JpaPersistenceConstants() {
    }
}
