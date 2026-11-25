package com.heuristiq.notification.domain.model;

import java.time.LocalDateTime;

import com.heuristiq.notification.domain.model.NotificationChannel;
import com.heuristiq.notification.domain.model.DeliveryStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class NotificationLog {

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

    private NotificationChannel channel;

    private String providerName;

    private String providerMessageId;

    private DeliveryStatus status;

    /** Email address / phone number / FCM token targeted */
    private String recipient;

    private boolean success;

    private int retryCount;

    private String errorMessage;

    private String renderedBody;

    private LocalDateTime sentAt;
}
