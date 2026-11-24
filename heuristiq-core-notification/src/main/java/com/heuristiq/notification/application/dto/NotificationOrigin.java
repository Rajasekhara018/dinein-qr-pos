package com.heuristiq.notification.application.dto;

import org.springframework.util.StringUtils;

/**
 * Who asked for a notification and why, as given by the caller. Every field is optional; callers
 * that pass nothing keep working. Values are trimmed and cut to a bounded length because the Kafka
 * path does not run bean validation.
 *
 * @param sourceService    calling service
 * @param eventCode        event or template code of the caller (defaults to the event key)
 * @param triggeringUserId user whose action triggered the message
 * @param entityType       type of the entity the message is about
 * @param entityId         id of that entity
 */
public record NotificationOrigin(String sourceService, String eventCode, String triggeringUserId,
        String entityType, String entityId) {

    private static final int MAX_LENGTH = 128;

    public static NotificationOrigin from(NotificationRequest request) {
        String event = clean(request.getEvent());
        String eventCode = clean(request.getEventCode());
        return new NotificationOrigin(
                clean(request.getSourceService()),
                eventCode != null ? eventCode : event,
                clean(request.getTriggeringUserId()),
                clean(request.getEntityType()),
                clean(request.getEntityId()));
    }

    private static String clean(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= MAX_LENGTH ? trimmed : trimmed.substring(0, MAX_LENGTH);
    }
}
