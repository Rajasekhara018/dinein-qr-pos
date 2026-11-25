package com.heuristiq.identity.notification.dto;

public record CommonNmsRequest(
        String messageID,
        NmsNotificationRequest requestObject) {
}
