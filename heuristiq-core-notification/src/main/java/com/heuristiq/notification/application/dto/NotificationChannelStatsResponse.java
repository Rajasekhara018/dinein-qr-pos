package com.heuristiq.notification.application.dto;

import com.heuristiq.notification.domain.model.NotificationChannel;

public record NotificationChannelStatsResponse(
        NotificationChannel channel,
        long success,
        long failed) {
}
