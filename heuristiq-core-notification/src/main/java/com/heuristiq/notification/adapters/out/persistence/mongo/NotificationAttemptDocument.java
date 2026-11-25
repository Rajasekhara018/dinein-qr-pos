package com.heuristiq.notification.adapters.out.persistence.mongo;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import com.heuristiq.notification.domain.model.DeliveryStatus;
import com.heuristiq.notification.domain.model.NotificationChannel;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Mongo document of one delivery attempt. See the domain {@code NotificationAttempt}. */
@Document(collection = "nms_delivery_attempts")
@CompoundIndex(name = "uk_nms_attempt_log_number", def = "{'logId': 1, 'attemptNumber': 1}", unique = true)
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class NotificationAttemptDocument {

    @Id
    private String id;

    /** Organization this record belongs to; null for a record stored before organizations existed. */
    @Indexed
    private String tenantId;

    private String logId;

    @Indexed
    private String messageId;

    @Indexed
    private String correlationId;

    private String event;

    private String sourceService;

    private String eventCode;

    private String triggeringUserId;

    private String entityType;

    private String entityId;

    private NotificationChannel channel;

    private String providerName;

    @Indexed
    private String providerMessageId;

    private int attemptNumber;

    private DeliveryStatus status;

    private String providerStatus;

    private String errorCode;

    private String errorMessage;

    /** Always masked. */
    private String recipient;

    private Long latencyMs;

    private LocalDateTime startedAt;

    private LocalDateTime completedAt;

    private LocalDateTime statusUpdatedAt;

    @Version
    private Long version;
}
