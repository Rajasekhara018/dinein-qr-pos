package com.heuristiq.notification.application.dto;

import com.heuristiq.notification.domain.model.NotificationChannel;

public record NotificationDispatchResult(
        NotificationChannel channel,
        boolean success,
        String error) {
}
