package com.heuristiq.notification.adapters.out.persistence.jpa;

import java.time.LocalDateTime;

import org.hibernate.annotations.UuidGenerator;

import com.heuristiq.notification.domain.model.DeliveryStatus;
import com.heuristiq.notification.domain.model.NotificationChannel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** JPA row of one delivery attempt. See the domain {@code NotificationAttempt}. */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "nms_delivery_attempts",
        uniqueConstraints = @UniqueConstraint(name = "uk_nms_attempt_log_number", columnNames = {"log_id", "attempt_number"}),
        indexes = {
                @Index(name = "idx_nms_attempt_tenant_id", columnList = "tenant_id"),
                @Index(name = "idx_nms_attempt_message_id", columnList = "message_id"),
                @Index(name = "idx_nms_attempt_correlation_id", columnList = "correlation_id"),
                @Index(name = "idx_nms_attempt_provider_msg_id", columnList = "provider_message_id"),
                @Index(name = "idx_nms_attempt_started_at", columnList = "started_at")
        })
public class NotificationAttemptEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    private String id;

    /** Organization this record belongs to; null for a record stored before organizations existed. */
    @Column(name = "tenant_id")
    private String tenantId;

    @Column(name = "log_id")
    private String logId;

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

    @Column(name = "provider_message_id")
    private String providerMessageId;

    @Column(name = "attempt_number")
    private int attemptNumber;

    @Enumerated(EnumType.STRING)
    private DeliveryStatus status;

    private String providerStatus;

    private String errorCode;

    @Column(length = 2000)
    private String errorMessage;

    /** Always masked. */
    private String recipient;

    private Long latencyMs;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    private LocalDateTime completedAt;

    private LocalDateTime statusUpdatedAt;

    @Version
    private Long version;
}
