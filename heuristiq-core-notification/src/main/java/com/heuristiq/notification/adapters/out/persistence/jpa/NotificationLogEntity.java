package com.heuristiq.notification.adapters.out.persistence.jpa;

import java.time.LocalDateTime;

import com.heuristiq.notification.domain.model.NotificationChannel;
import com.heuristiq.notification.domain.model.DeliveryStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "nms_logs", indexes = {
                @Index(name = "idx_nms_log_tenant_id", columnList = "tenant_id"),
        @Index(name = "idx_nms_log_message_id", columnList = "message_id"),
        @Index(name = "idx_nms_log_correlation_id", columnList = "correlation_id"),
        @Index(name = "idx_nms_log_event", columnList = "event"),
        @Index(name = "idx_nms_log_sent_at", columnList = "sent_at")
})
public class NotificationLogEntity {

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
    private NotificationChannel channel;

    private String providerName;

    private String providerMessageId;

    @Enumerated(EnumType.STRING)
    private DeliveryStatus status;

    /** Email address / phone number / FCM token targeted */
    private String recipient;

    private boolean success;

    private int retryCount;

    @Column(length = 2000)
    private String errorMessage;

    @Column(length = 4000)
    private String renderedBody;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;
}
