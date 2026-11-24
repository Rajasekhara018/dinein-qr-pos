package com.railcab.nms.application.usecase;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.railcab.nms.application.dto.DeliveryReceipt;
import com.railcab.nms.application.dto.VoiceCallback;
import com.railcab.nms.application.dto.VoiceResponseEvent;
import com.railcab.nms.application.port.in.DeliveryReceiptUseCase;
import com.railcab.nms.application.port.in.VoiceCallbackUseCase;
import com.railcab.nms.application.port.out.VoiceResponsePublisher;
import com.railcab.nms.application.usecase.support.VoiceOutcomeMapper;
import com.railcab.nms.domain.model.NotificationAttempt;
import com.railcab.nms.domain.model.NotificationChannel;
import com.railcab.nms.domain.model.VoiceCallOutcome;
import com.railcab.nms.domain.repository.INotificationAttemptRepository;

import lombok.RequiredArgsConstructor;

/**
 * Turns a voice provider's call report into (1) a delivery receipt on the call's attempt (answered = DELIVERED, otherwise
 * FAILED) and (2) a {@link VoiceResponseEvent} on {@code nms-voice-responses} carrying the outcome and pressed digit.
 *
 * <p>The entity the call is about comes from the call itself (the provider echoes it), else from the attempt recorded
 * when the call was placed. The event id is derived from the call id and the reported status, so a repeated report
 * publishes the same id and consumers stay idempotent.
 */
@Service
@RequiredArgsConstructor
public class VoiceCallbackService implements VoiceCallbackUseCase {

    private static final Logger logger = LoggerFactory.getLogger(VoiceCallbackService.class);

    private final DeliveryReceiptUseCase receipts;
    private final INotificationAttemptRepository attemptRepository;
    private final VoiceResponsePublisher publisher;
    private final Clock clock;

    @Override
    public Result handle(VoiceCallback callback) {
        if (callback == null || !StringUtils.hasText(callback.callSid())) {
            return Result.IGNORED;
        }
        String digit = VoiceOutcomeMapper.digit(callback.rawDigits());
        Optional<VoiceCallOutcome> outcome = VoiceOutcomeMapper.outcome(callback.rawStatus(), digit);
        if (outcome.isEmpty()) {
            logger.info("Voice callback for call {} carried no status or digit NMS tracks; ignored", callback.callSid());
            return Result.IGNORED;
        }
        VoiceCallOutcome result = outcome.get();
        String rawStatus = StringUtils.hasText(callback.rawStatus()) ? callback.rawStatus().trim() : result.name();
        receipts.apply(new DeliveryReceipt(callback.provider(), callback.callSid(), result.deliveryStatus(), rawStatus,
                result == VoiceCallOutcome.ANSWERED ? null : rawStatus, result == VoiceCallOutcome.ANSWERED ? null : rawStatus, null));

        // Who the answer is about comes only from NMS's own record of the call it placed, never from the report: the
        // provider-echoed CustomField is caller-controlled, so trusting it would let a forged report answer for any ride.
        NotificationAttempt attempt = attemptRepository.findLatestByProviderMessageId(callback.callSid())
                .filter(found -> found.getChannel() == NotificationChannel.VOICE)
                .orElse(null);
        if (attempt == null) {
            logger.warn("Voice callback for call {} matches no voice call NMS placed; recorded only", callback.callSid());
            return Result.UNKNOWN_CALL;
        }
        String entityType = attempt.getEntityType();
        String entityId = attempt.getEntityId();
        String eventCode = firstText(attempt.getEventCode(), attempt.getEvent());
        if (!StringUtils.hasText(entityId)) {
            logger.info("Voice callback for call {} has no entity to report to; recorded only", callback.callSid());
            return Result.NO_ENTITY;
        }
        VoiceResponseEvent event = new VoiceResponseEvent(
                VoiceOutcomeMapper.eventId(callback.callSid(), rawStatus, digit),
                entityType, entityId, eventCode, digit, result.name(), Instant.now(clock).toString(),
                attempt.getTenantId());
        if (!publisher.publish(event)) {
            logger.warn("Voice response {} could not be published", event.eventId());
            return Result.PUBLISH_FAILED;
        }
        logger.info("Voice response {} published: outcome={} digitPresent={}", event.eventId(), result, digit != null);
        return Result.PUBLISHED;
    }

    private static String firstText(String first, String second) {
        return StringUtils.hasText(first) ? first : (StringUtils.hasText(second) ? second : null);
    }
}
