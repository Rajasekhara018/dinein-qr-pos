package com.heuristiq.notification.domain.model;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class InAppNotification {

    private String id;

    private String userId;

    /** Organization this record belongs to; null for a record stored before organizations existed. */
    private String tenantId;

    private String title;

    private String message;

    private boolean read;

    private LocalDateTime createdAt;
}
