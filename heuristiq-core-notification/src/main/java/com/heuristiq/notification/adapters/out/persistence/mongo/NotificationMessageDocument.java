package com.railcab.nms.adapters.out.persistence.mongo;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import com.railcab.nms.domain.model.NotificationMessageStatus;

import lombok.Data;

@Document(collection = "nms_messages")
@Data
public class NotificationMessageDocument {

    @Id
    private String id;

    /** Organization this record belongs to; null for a record stored before organizations existed. */
    @Indexed
    private String tenantId;

    @Indexed(unique = true)
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

    private NotificationMessageStatus status;

    private String errorMessage;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime completedAt;
}
