package com.heuristiq.notification.application.usecase.support;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heuristiq.notification.application.dto.CommunicationAuditEvent;
import com.heuristiq.notification.domain.model.AuditOutboxEntry;
import com.heuristiq.notification.domain.model.AuditOutboxStatus;
import com.heuristiq.notification.domain.model.DeliveryStatus;
import com.heuristiq.notification.domain.model.NotificationAttempt;
import com.heuristiq.notification.domain.repository.IAuditOutboxRepository;
import com.heuristiq.notification.infrastructure.config.NmsProperties;

import lombok.RequiredArgsConstructor;

/**
 * Turns an attempt outcome or a status update into a COMMUNICATION audit event and parks it in the
 * audit outbox. Sending happens later, off the request path ({@link com.heuristiq.notification.application.usecase.AuditOutboxService}),
 * so this class never waits for the audit service and never throws: a failure here is logged and
 * the notification flow carries on.
 *
 * <p>Event id: the attempt id for the outcome of the send itself, and {@code attemptId_STATUS} for
 * each later status update. The same attempt and status always give the same id, so re-enqueueing
 * or re-sending is absorbed by the audit service's idempotency.
 */
@Component
@RequiredArgsConstructor
public class CommunicationAuditPublisher {

    private static final Logger logger = LoggerFactory.getLogger(CommunicationAuditPublisher.class);

    private final IAuditOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final NmsProperties props;
    private final Clock clock;

    /** Records the outcome of the send call itself. */
    public void publishAttemptOutcome(NotificationAttempt attempt) {
        publish(attempt, attempt.getId());
    }

    /** Records a status change reported by a provider receipt. */
    public void publishStatusUpdate(NotificationAttempt attempt) {
        String suffix = attempt.getStatus() == null ? "UNKNOWN" : attempt.getStatus().name();
        publish(attempt, attempt.getId() + CommunicationAuditConstants.EVENT_ID_SEPARATOR + suffix);
    }

    private void publish(NotificationAttempt attempt, String rawEventId) {
        NmsProperties.Audit.Publish cfg = props.getAudit().getPublish();
        if (!cfg.isEnabled() || attempt == null || !StringUtils.hasText(rawEventId)) {
            return;
        }
        try {
            String eventId = boundedEventId(rawEventId);
            CommunicationAuditEvent event = toEvent(attempt, eventId);
            LocalDateTime now = LocalDateTime.now(clock);
            boolean stored = outboxRepository.insertIfAbsent(AuditOutboxEntry.builder()
                    .eventId(eventId)
                    .payload(objectMapper.writeValueAsString(event))
                    .status(AuditOutboxStatus.PENDING)
                    .attempts(0)
                    .nextAttemptAt(now)
                    .createdAt(now)
                    .expireAt(now.plusDays(cfg.getOutboxRetentionDays()))
                    .build());
            if (!stored) {
                logger.debug("Audit event {} already queued", eventId);
            }
        } catch (Exception e) {
            // Deliberate degrade: audit is best-effort next to the send, never a reason to fail it.
            logger.warn("Could not queue communication audit event for attempt {}: {}", attempt.getId(), e.getMessage());
        }
    }

    CommunicationAuditEvent toEvent(NotificationAttempt a, String eventId) {
        Map<String, String> details = new LinkedHashMap<>();
        put(details, CommunicationAuditConstants.D_CHANNEL, a.getChannel() == null ? null : a.getChannel().name());
        put(details, CommunicationAuditConstants.D_TEMPLATE_CODE, a.getEventCode() != null ? a.getEventCode() : a.getEvent());
        put(details, CommunicationAuditConstants.D_ORIGIN_SERVICE, a.getSourceService());
        put(details, CommunicationAuditConstants.D_RECIPIENT, RecipientMasker.mask(a.getRecipient()));
        put(details, CommunicationAuditConstants.D_PROVIDER, a.getProviderName());
        put(details, CommunicationAuditConstants.D_ATTEMPT, String.valueOf(a.getAttemptNumber()));
        put(details, CommunicationAuditConstants.D_STATUS, a.getStatus() == null ? null : a.getStatus().name());
        put(details, CommunicationAuditConstants.D_PROVIDER_STATUS, a.getProviderStatus());
        put(details, CommunicationAuditConstants.D_ERROR_CODE, a.getErrorCode());
        put(details, CommunicationAuditConstants.D_FAILURE_REASON,
                ErrorSanitizer.sanitize(a.getErrorMessage(), CommunicationAuditConstants.FAILURE_REASON_MAX_LENGTH));
        put(details, CommunicationAuditConstants.D_MESSAGE_ID, a.getMessageId());
        put(details, CommunicationAuditConstants.D_PROVIDER_MESSAGE_ID, a.getProviderMessageId());
        put(details, CommunicationAuditConstants.D_ENTITY_TYPE, a.getEntityType());
        put(details, CommunicationAuditConstants.D_ENTITY_ID, a.getEntityId());

        DeliveryStatus status = a.getStatus();
        String outcome = status != null && status.isFailure()
                ? CommunicationAuditConstants.OUTCOME_FAILURE
                : CommunicationAuditConstants.OUTCOME_SUCCESS;
        LocalDateTime when = a.getStatusUpdatedAt() != null ? a.getStatusUpdatedAt()
                : a.getCompletedAt() != null ? a.getCompletedAt() : a.getStartedAt();
        Instant occurredAt = when == null ? clock.instant() : when.atZone(clock.getZone()).toInstant();
        return new CommunicationAuditEvent(
                eventId,
                CommunicationAuditConstants.EVENT_TYPE,
                occurredAt,
                CommunicationAuditConstants.SOURCE_SERVICE,
                a.getTriggeringUserId(),
                a.getTenantId(),
                CommunicationAuditConstants.FEATURE,
                CommunicationAuditConstants.MODULE,
                outcome,
                a.getLatencyMs(),
                a.getCorrelationId(),
                a.getMessageId(),
                CommunicationAuditConstants.APP_CHANNEL,
                details);
    }

    private static String boundedEventId(String raw) {
        String safe = raw.replaceAll("[^A-Za-z0-9_-]", "-");
        return safe.length() <= CommunicationAuditConstants.EVENT_ID_MAX_LENGTH
                ? safe
                : safe.substring(0, CommunicationAuditConstants.EVENT_ID_MAX_LENGTH);
    }

    private static void put(Map<String, String> map, String key, String value) {
        if (StringUtils.hasText(value)) {
            map.put(key, value);
        }
    }
}
