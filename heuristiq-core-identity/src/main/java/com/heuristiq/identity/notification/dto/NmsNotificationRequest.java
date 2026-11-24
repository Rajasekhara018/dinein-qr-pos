package com.heuristiq.uam.notification.dto;

import java.util.List;
import java.util.Map;


/**
 * The body NMS accepts at {@code /api/v1/nms/notify}. The last five fields are the message's origin (who asked, about what,
 * on behalf of whom, in which request) so NMS can trace it from origin to destination; they are optional.
 */
public record NmsNotificationRequest(
        String event,
        Map<String, String> variables,
        List<String> toEmail,
        String toPhone,
        List<String> fcmTokens,
        List<String> channels,
        String sourceService,
        String eventCode,
        String tenantId,
        String triggeringUserId,
        String entityType,
        String entityId,
        String correlationId) {

    /** A request with no entity or user origin (OTP, welcome and account messages). */
    public NmsNotificationRequest(String event, Map<String, String> variables, List<String> toEmail, String toPhone,
                                  List<String> fcmTokens, List<String> channels, String sourceService, String eventCode,
                                  String tenantId) {
        this(event, variables, toEmail, toPhone, fcmTokens, channels, sourceService, eventCode, tenantId, null, null, null, null);
    }
}
