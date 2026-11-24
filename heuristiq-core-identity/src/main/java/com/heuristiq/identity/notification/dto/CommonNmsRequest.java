package com.heuristiq.uam.notification.dto;

public record CommonNmsRequest(
        String messageID,
        NmsNotificationRequest requestObject) {
}
