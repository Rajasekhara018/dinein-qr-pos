package com.railcab.nms.application.dto;

import com.railcab.nms.domain.model.NotificationChannel;

public record NotificationChannelStatsResponse(
        NotificationChannel channel,
        long success,
        long failed) {
}
