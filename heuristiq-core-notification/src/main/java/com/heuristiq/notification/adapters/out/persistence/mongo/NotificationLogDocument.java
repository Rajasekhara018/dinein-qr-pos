package com.railcab.nms.adapters.out.persistence.mongo;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import com.railcab.nms.domain.model.NotificationChannel;
import com.railcab.nms.domain.model.DeliveryStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Document(collection = "nms_logs")
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class NotificationLogDocument {

    @Id
    private String id;

    /** Organization this record belongs to; null for a record stored before organizations existed. */
    @Indexed
    private String tenantId;

    @Indexed
    private String messageId;

    @Indexed
    private String correlationId;

    @Indexed
    private String event;

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
