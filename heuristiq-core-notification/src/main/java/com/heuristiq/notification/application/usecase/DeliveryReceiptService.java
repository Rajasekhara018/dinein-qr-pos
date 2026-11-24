package com.heuristiq.notification.application.usecase;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.heuristiq.notification.application.dto.DeliveryReceipt;
import com.heuristiq.notification.application.dto.ReceiptOutcome;
import com.heuristiq.notification.application.port.in.DeliveryReceiptUseCase;
import com.heuristiq.notification.application.usecase.support.CommunicationAuditPublisher;
import com.heuristiq.notification.application.usecase.support.ErrorSanitizer;
import com.heuristiq.notification.domain.model.DeliveryStatus;
import com.heuristiq.notification.domain.model.NotificationAttempt;
import com.heuristiq.notification.domain.model.NotificationLog;
import com.heuristiq.notification.domain.repository.INotificationAttemptRepository;
import com.heuristiq.notification.domain.repository.INotificationLogRepository;

import lombok.RequiredArgsConstructor;

/**
 * Applies a provider delivery receipt to the attempt it refers to, and mirrors it on the
 * per-recipient log row.
 *
 * <p>Idempotent: a receipt that does not move the status forward (a repeat, or one that arrives
 * after a later status) changes nothing and publishes nothing. Two receipts racing for the same
 * attempt are serialised by the attempt's optimistic lock; the loser re-reads and re-evaluates.
 */
@Service
@RequiredArgsConstructor
public class DeliveryReceiptService implements DeliveryReceiptUseCase {

    private static final Logger logger = LoggerFactory.getLogger(DeliveryReceiptService.class);

    private final INotificationAttemptRepository attemptRepository;
    private final INotificationLogRepository logRepository;
    private final CommunicationAuditPublisher auditPublisher;
    private final Clock clock;

    @Override
    public ReceiptOutcome apply(DeliveryReceipt receipt) {
        if (receipt == null || !StringUtils.hasText(receipt.providerMessageId()) || receipt.status() == null) {
            return ReceiptOutcome.INVALID;
        }
        for (int tries = 0; tries < NotificationUseCaseConstants.RECEIPT_MAX_LOCK_RETRIES; tries++) {
            Optional<NotificationAttempt> found = attemptRepository.findLatestByProviderMessageId(receipt.providerMessageId());
            if (found.isEmpty()) {
                logger.info("Delivery receipt for unknown providerMessageId (provider={})", receipt.provider());
                return ReceiptOutcome.UNKNOWN_MESSAGE;
            }
            NotificationAttempt attempt = found.get();
            if (!DeliveryStatus.canAdvance(attempt.getStatus(), receipt.status())) {
                return ReceiptOutcome.DUPLICATE;
            }
            try {
                NotificationAttempt updated = attemptRepository.save(withReceipt(attempt, receipt));
                mirrorOnLog(updated);
                auditPublisher.publishStatusUpdate(updated);
                return ReceiptOutcome.APPLIED;
            } catch (OptimisticLockingFailureException e) {
                logger.debug("Receipt raced another update for attempt {}, re-evaluating", attempt.getId());
            }
        }
        // Still contended after several tries: another writer is applying receipts for this attempt.
        return ReceiptOutcome.DUPLICATE;
    }

    private NotificationAttempt withReceipt(NotificationAttempt attempt, DeliveryReceipt receipt) {
        LocalDateTime at = receipt.occurredAt() != null ? receipt.occurredAt() : LocalDateTime.now(clock);
        NotificationAttempt.NotificationAttemptBuilder builder = attempt.toBuilder()
                .status(receipt.status())
                .providerStatus(cut(receipt.providerStatus()))
                .statusUpdatedAt(at);
        if (receipt.status().isFailure()) {
            builder.errorCode(cut(receipt.errorCode()))
                    .errorMessage(ErrorSanitizer.sanitize(receipt.errorMessage(), NotificationUseCaseConstants.RECEIPT_MAX_FIELD_LENGTH));
        }
        return builder.build();
    }

    /** Keeps the summary row in step: a late failure receipt turns a "sent" row into a failed one. */
    private void mirrorOnLog(NotificationAttempt attempt) {
        if (!StringUtils.hasText(attempt.getLogId())) {
            return;
        }
        try {
            Optional<NotificationLog> found = logRepository.findById(attempt.getLogId());
            if (found.isEmpty() || !DeliveryStatus.canAdvance(found.get().getStatus(), attempt.getStatus())) {
                return;
            }
            NotificationLog log = found.get();
            log.setStatus(attempt.getStatus());
            if (attempt.getStatus().isFailure()) {
                log.setSuccess(false);
                log.setErrorMessage(attempt.getErrorMessage());
            }
            logRepository.save(log);
        } catch (RuntimeException e) {
            // The attempt row is the source of truth; a stale summary is acceptable.
            logger.warn("Could not mirror receipt on log {}: {}", attempt.getLogId(), e.getMessage());
        }
    }

    private static String cut(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.length() <= NotificationUseCaseConstants.RECEIPT_MAX_FIELD_LENGTH ? value : value.substring(0, NotificationUseCaseConstants.RECEIPT_MAX_FIELD_LENGTH);
    }
}
