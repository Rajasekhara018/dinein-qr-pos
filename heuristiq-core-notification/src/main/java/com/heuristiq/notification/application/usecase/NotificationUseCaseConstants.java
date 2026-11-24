package com.railcab.nms.application.usecase;

/** Fixed limits of NMS's application services (audit outbox drain and delivery-receipt handling). */
final class NotificationUseCaseConstants {

    /** Longest sanitised failure reason stored on an audit outbox entry after a failed push. */
    static final int AUDIT_OUTBOX_ERROR_MAX_LENGTH = 300;

    /** How often a delivery receipt is re-applied after an optimistic-lock conflict before giving up. */
    static final int RECEIPT_MAX_LOCK_RETRIES = 3;

    /** Longest provider status / error text kept from a delivery receipt. */
    static final int RECEIPT_MAX_FIELD_LENGTH = 200;

    private NotificationUseCaseConstants() {
    }
}
