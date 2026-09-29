package com.heuristq.dinein.notification;

/** Business events that produce notifications; the name doubles as the template code in {@code notification_log}. */
public enum NotificationEvent {
    ORDER_CONFIRMED(NotificationSeverity.INFO),
    ORDER_READY(NotificationSeverity.INFO),
    ORDER_CANCELLED(NotificationSeverity.INFO),
    PAYMENT_FLAGGED(NotificationSeverity.HIGH),
    REFUND_FAILED(NotificationSeverity.HIGH),
    DAILY_SUMMARY(NotificationSeverity.INFO),
    KDS_DEVICE_OFFLINE(NotificationSeverity.HIGH);

    private final NotificationSeverity severity;

    NotificationEvent(NotificationSeverity severity) {
        this.severity = severity;
    }

    public NotificationSeverity severity() {
        return severity;
    }
}
