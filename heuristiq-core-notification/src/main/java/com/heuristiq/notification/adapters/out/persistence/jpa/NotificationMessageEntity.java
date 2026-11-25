package com.heuristiq.notification.adapters.out.persistence.jpa;

import java.time.LocalDateTime;

import com.heuristiq.notification.domain.model.NotificationMessageStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import org.hibernate.annotations.UuidGenerator;

@Data
@Entity
@Table(name = "nms_messages",
        uniqueConstraints = @UniqueConstraint(name = "uk_nms_message_message_id", columnNames = "message_id"),
        indexes = {
                @Index(name = "idx_nms_message_tenant_id", columnList = "tenant_id"),
                @Index(name = "idx_nms_message_correlation_id", columnList = "correlation_id"),
                @Index(name = "idx_nms_message_event", columnList = "event"),
                @Index(name = "idx_nms_message_completed_at", columnList = "completed_at")
        })
public class NotificationMessageEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    private String id;

    /** Organization this record belongs to; null for a record stored before organizations existed. */
    @Column(name = "tenant_id")
    private String tenantId;

    @Column(name = "message_id")
    private String messageId;

    @Column(name = "correlation_id")
    private String correlationId;

    private String event;

    private String sourceService;

    private String eventCode;

    private String triggeringUserId;

    private String entityType;

    private String entityId;

    @Enumerated(EnumType.STRING)
    private NotificationMessageStatus status;

    @Column(length = 2000)
    private String errorMessage;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
