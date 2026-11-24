package com.railcab.nms.application.dto;

import com.railcab.nms.domain.model.NotificationChannel;

public record NotificationDispatchResult(
        NotificationChannel channel,
        boolean success,
        String error) {
}
