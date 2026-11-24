package com.heuristiq.notification.application.usecase.support;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.heuristiq.notification.application.dto.ChannelSendOutcome;
import com.heuristiq.notification.application.dto.ChannelSendOutcome.AttemptResult;
import com.heuristiq.notification.application.dto.NotificationOrigin;
import com.heuristiq.notification.application.dto.NotificationRequest;
import com.heuristiq.notification.application.port.out.ChannelProvider;
import com.heuristiq.notification.domain.model.DeliveryStatus;
import com.heuristiq.notification.domain.model.NotificationAttempt;
import com.heuristiq.notification.domain.model.NotificationLog;
import com.heuristiq.notification.domain.repository.INotificationAttemptRepository;
import com.heuristiq.notification.domain.repository.INotificationLogRepository;
import com.heuristiq.notification.infrastructure.config.NmsProperties;

import lombok.RequiredArgsConstructor;

/**
 * Owns delivery-record persistence and recipient PII masking for dispatch attempts -- split out of
 * {@link com.heuristiq.notification.application.usecase.NotificationDispatchService} so audit/masking rules can
 * change without touching orchestration logic, and vice versa.
 *
 * <p>Per recipient and channel it writes one {@link NotificationLog} summary row (unchanged
 * contract for the existing {@code /logs} endpoints) and one {@link NotificationAttempt} per provider
 * call, then queues a COMMUNICATION audit event for each attempt. Attempt and audit writes are
 * best-effort: they never fail the send.
 */
@Component
@RequiredArgsConstructor
public class NotificationAuditRecorder {

    private static final Logger logger = LoggerFactory.getLogger(NotificationAuditRecorder.class);

    private final INotificationLogRepository logRepository;
    private final INotificationAttemptRepository attemptRepository;
    private final CommunicationAuditPublisher auditPublisher;
    private final NmsProperties props;
    private final Clock clock;

    public void record(NotificationRequest request, ChannelProvider provider, String recipient,
            String renderedBody, ChannelSendOutcome outcome) {
        NotificationOrigin origin = NotificationOrigin.from(request);
        List<AttemptResult> attempts = effectiveAttempts(outcome);
        AttemptResult last = attempts.get(attempts.size() - 1);
        AttemptResult accepted = attempts.stream().filter(AttemptResult::success).findFirst().orElse(null);
        String providerName = firstNonNull(
                accepted == null ? null : accepted.providerName(),
                last.providerName(),
                provider.getClass().getSimpleName());

        NotificationLog log = logRepository.save(NotificationLog.builder()
                .tenantId(request.getTenantId())
                .messageId(request.getMessageId())
                .correlationId(request.getCorrelationId())
                .event(request.getEvent())
                .sourceService(origin.sourceService())
                .eventCode(origin.eventCode())
                .triggeringUserId(origin.triggeringUserId())
                .entityType(origin.entityType())
                .entityId(origin.entityId())
                .channel(provider.getChannel())
                .providerName(providerName)
                .providerMessageId(accepted == null ? null : accepted.providerMessageId())
                .status(outcome.success() ? DeliveryStatus.SENT : DeliveryStatus.FAILED)
                .recipient(maskRecipient(recipient))
                .success(outcome.success())
                .retryCount(Math.max(0, outcome.attempts() - 1))
                .errorMessage(outcome.success() ? null : outcome.error())
                .renderedBody(props.getAudit().isStoreRenderedBody() ? renderedBody : null)
                .sentAt(LocalDateTime.now(clock))
                .build());

        recordAttempts(log, request, origin, provider, recipient, attempts, providerName);
    }

    private void recordAttempts(NotificationLog log, NotificationRequest request, NotificationOrigin origin,
            ChannelProvider provider, String recipient, List<AttemptResult> attempts, String fallbackProvider) {
        String masked = RecipientMasker.mask(recipient);
        for (AttemptResult a : attempts) {
            try {
                LocalDateTime started = a.startedAt() == null ? LocalDateTime.now(clock) : a.startedAt();
                NotificationAttempt saved = attemptRepository.save(NotificationAttempt.builder()
                        .logId(log.getId())
                        .tenantId(log.getTenantId())
                        .messageId(request.getMessageId())
                        .correlationId(request.getCorrelationId())
                        .event(request.getEvent())
                        .sourceService(origin.sourceService())
                        .eventCode(origin.eventCode())
                        .triggeringUserId(origin.triggeringUserId())
                        .entityType(origin.entityType())
                        .entityId(origin.entityId())
                        .channel(provider.getChannel())
                        .providerName(firstNonNull(a.providerName(), fallbackProvider))
                        .providerMessageId(a.providerMessageId())
                        .attemptNumber(a.attemptNumber())
                        .status(a.success() ? DeliveryStatus.SENT : DeliveryStatus.FAILED)
                        .providerStatus(a.providerStatus())
                        .errorCode(a.success() ? null : a.errorCode())
                        .errorMessage(a.success() ? null : a.errorMessage())
                        .recipient(masked)
                        .latencyMs(a.latencyMs())
                        .startedAt(started)
                        .completedAt(started.plusNanos(a.latencyMs() * 1_000_000L))
                        .build());
                auditPublisher.publishAttemptOutcome(saved);
            } catch (RuntimeException e) {
                // Deliberate degrade: the attempt trail is diagnostics, the notification already went out (or failed).
                logger.warn("Could not record attempt {} for log {}: {}", a.attemptNumber(), log.getId(), e.getMessage());
            }
        }
    }

    /** A caller that did not collect per-attempt detail still gets one attempt that mirrors the summary. */
    private List<AttemptResult> effectiveAttempts(ChannelSendOutcome outcome) {
        if (!outcome.attemptResults().isEmpty()) {
            return outcome.attemptResults();
        }
        return List.of(new AttemptResult(Math.max(1, outcome.attempts()), outcome.success(), null, null, null,
                outcome.success() ? null : "PROVIDER_REJECTED", outcome.success() ? null : outcome.error(),
                LocalDateTime.now(clock), 0L));
    }

    private String maskRecipient(String recipient) {
        if (!props.getAudit().isMaskRecipients() || recipient == null || recipient.length() <= 4) {
            return recipient;
        }
        int visible = Math.min(4, recipient.length());
        return "*".repeat(recipient.length() - visible) + recipient.substring(recipient.length() - visible);
    }

    private static String firstNonNull(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }
}
