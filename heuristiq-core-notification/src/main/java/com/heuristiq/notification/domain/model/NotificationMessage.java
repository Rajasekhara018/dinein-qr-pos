package com.heuristiq.notification.domain.model;

import java.time.LocalDateTime;

import com.heuristiq.notification.domain.model.NotificationMessageStatus;

import lombok.Data;

@Data
public class NotificationMessage {

    private String id;

    private String messageId;

    /** Organization this record belongs to; null for a record stored before organizations existed. */
    private String tenantId;

    private String correlationId;

    private String event;

    /** Origin of the request (all optional, see NotificationRequest). */
    private String sourceService;

    private String eventCode;

    private String triggeringUserId;

    private String entityType;

    private String entityId;

    private NotificationMessageStatus status;

    private String errorMessage;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime completedAt;
}
