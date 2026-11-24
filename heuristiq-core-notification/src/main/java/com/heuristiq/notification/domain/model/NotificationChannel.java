package com.railcab.nms.domain.model;

/**
 * Delivery channels NMS can send on. Stored by name (JPA {@code EnumType.STRING}, Mongo string), so a new value needs no
 * schema change.
 */
public enum NotificationChannel {
    EMAIL,
    SMS,
    WHATSAPP,
    FCM_PUSH,
    IN_APP,
    /** Automated phone call (IVR flow) to {@code toPhone}; provider selected by {@code nms.provider.voice}. */
    VOICE
}
