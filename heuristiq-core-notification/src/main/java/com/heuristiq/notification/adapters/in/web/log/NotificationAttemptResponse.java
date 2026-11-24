package com.heuristiq.notification.adapters.in.web.log;

import java.time.LocalDateTime;

import com.heuristiq.notification.domain.model.DeliveryStatus;
import com.heuristiq.notification.domain.model.NotificationAttempt;
import com.heuristiq.notification.domain.model.NotificationChannel;

import io.swagger.v3.oas.annotations.media.Schema;

/** One delivery attempt as the admin API returns it. The recipient is always masked. */
@Schema(description = "One provider send attempt, from origin to destination")
public record NotificationAttemptResponse(
        String id,
        String messageId,
        String correlationId,
        String event,
        String sourceService,
        String eventCode,
        String triggeringUserId,
        String entityType,
        String entityId,
        NotificationChannel channel,
        String providerName,
        String providerMessageId,
        int attemptNumber,
        DeliveryStatus status,
        String providerStatus,
        String errorCode,
        String errorMessage,
        String recipient,
        Long latencyMs,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        LocalDateTime statusUpdatedAt) {

    static NotificationAttemptResponse from(NotificationAttempt a) {
        return new NotificationAttemptResponse(a.getId(), a.getMessageId(), a.getCorrelationId(), a.getEvent(),
                a.getSourceService(), a.getEventCode(), a.getTriggeringUserId(), a.getEntityType(), a.getEntityId(),
                a.getChannel(), a.getProviderName(), a.getProviderMessageId(), a.getAttemptNumber(), a.getStatus(),
                a.getProviderStatus(), a.getErrorCode(), a.getErrorMessage(), a.getRecipient(), a.getLatencyMs(),
                a.getStartedAt(), a.getCompletedAt(), a.getStatusUpdatedAt());
    }
}
