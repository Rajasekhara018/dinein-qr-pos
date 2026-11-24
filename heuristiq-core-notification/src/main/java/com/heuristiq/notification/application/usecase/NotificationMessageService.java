package com.railcab.nms.application.usecase;

import java.time.LocalDateTime;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.railcab.nms.infrastructure.config.NmsProperties;
import com.railcab.nms.domain.model.NotificationMessage;
import com.railcab.nms.domain.model.NotificationMessageStatus;
import com.railcab.nms.domain.repository.INotificationMessageRepository;
import com.railcab.nms.application.dto.NotificationOrigin;
import com.railcab.nms.application.dto.NotificationRequest;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NotificationMessageService {

    private final INotificationMessageRepository repository;
    private final NmsProperties props;

    public Reservation reserve(NotificationRequest request) {
        String messageId = request.getMessageId();
        if (!StringUtils.hasText(messageId)) {
            return Reservation.accepted(null);
        }

        LocalDateTime now = LocalDateTime.now();
        NotificationMessage message = new NotificationMessage();
        message.setMessageId(messageId);
        message.setTenantId(request.getTenantId());
        message.setCorrelationId(request.getCorrelationId());
        message.setEvent(request.getEvent());
        NotificationOrigin origin = NotificationOrigin.from(request);
        message.setSourceService(origin.sourceService());
        message.setEventCode(origin.eventCode());
        message.setTriggeringUserId(origin.triggeringUserId());
        message.setEntityType(origin.entityType());
        message.setEntityId(origin.entityId());
        message.setStatus(NotificationMessageStatus.PROCESSING);
        message.setCreatedAt(now);
        message.setUpdatedAt(now);

        try {
            return Reservation.accepted(repository.insert(message));
        } catch (DataIntegrityViolationException ex) {
            NotificationMessage existing = repository.findByMessageId(messageId).orElse(null);
            if (existing != null
                    && existing.getStatus() == NotificationMessageStatus.FAILED
                    && props.getIdempotency().isRetryFailedMessages()) {
                return Reservation.accepted(reopen(existing, now, "Retrying failed message"));
            }
            NotificationMessage stale = repository.findFirstByMessageIdAndStatusAndUpdatedAtBefore(
                    messageId,
                    NotificationMessageStatus.PROCESSING,
                    now.minusMinutes(props.getIdempotency().getProcessingTimeoutMinutes()))
                    .orElse(null);
            if (stale != null) {
                return Reservation.accepted(reopen(stale, now, "Retrying stale processing message"));
            }
            return Reservation.duplicate(existing);
        }
    }

    public void complete(String messageId, boolean success, String errorMessage) {
        if (!StringUtils.hasText(messageId)) {
            return;
        }
        repository.findByMessageId(messageId).ifPresent(message -> {
            LocalDateTime now = LocalDateTime.now();
            message.setStatus(success ? NotificationMessageStatus.SENT : NotificationMessageStatus.FAILED);
            message.setErrorMessage(success ? null : errorMessage);
            message.setUpdatedAt(now);
            message.setCompletedAt(now);
            repository.save(message);
        });
    }

    public record Reservation(boolean accepted, NotificationMessage message, boolean duplicate) {
        static Reservation accepted(NotificationMessage message) {
            return new Reservation(true, message, false);
        }

        static Reservation duplicate(NotificationMessage message) {
            return new Reservation(false, message, true);
        }
    }

    private NotificationMessage reopen(NotificationMessage message, LocalDateTime now, String reason) {
        message.setStatus(NotificationMessageStatus.PROCESSING);
        message.setErrorMessage(reason);
        message.setUpdatedAt(now);
        message.setCompletedAt(null);
        return repository.save(message);
    }
}
