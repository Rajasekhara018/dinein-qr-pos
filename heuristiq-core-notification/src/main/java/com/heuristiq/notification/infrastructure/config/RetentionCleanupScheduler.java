package com.heuristiq.notification.infrastructure.config;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.heuristiq.notification.domain.repository.IAuditOutboxRepository;
import com.heuristiq.notification.domain.repository.INotificationAttemptRepository;
import com.heuristiq.notification.domain.repository.INotificationLogRepository;
import com.heuristiq.notification.domain.repository.INotificationMessageRepository;

import lombok.RequiredArgsConstructor;

/**
 * Persistence-agnostic retention sweep. Mongo already expires {@code nms_logs}/{@code nms_messages}
 * natively via TTL indexes (see {@code NotificationLogIndexConfig}/{@code NotificationMessageIndexConfig}),
 * so the Mongo adapters' {@code purgeOlderThan} is a no-op; the JPA/MySQL adapters have no TTL
 * equivalent and run the actual bulk delete here.
 */
@Component
@RequiredArgsConstructor
public class RetentionCleanupScheduler {

    private static final Logger logger = LoggerFactory.getLogger(RetentionCleanupScheduler.class);

    private final INotificationLogRepository logRepository;
    private final INotificationMessageRepository messageRepository;
    private final INotificationAttemptRepository attemptRepository;
    private final IAuditOutboxRepository auditOutboxRepository;
    private final NmsProperties props;

    @Scheduled(cron = "${nms.retention.cleanup-cron:0 30 2 * * *}")
    public void purgeExpiredRecords() {
        LocalDateTime logCutoff = LocalDateTime.now().minusDays(props.getAudit().getLogRetentionDays());
        LocalDateTime messageCutoff = LocalDateTime.now().minusDays(props.getIdempotency().getMessageRetentionDays());

        logger.debug("RetentionCleanupScheduler: purging logs before {} and messages before {}", logCutoff, messageCutoff);
        logRepository.purgeOlderThan(logCutoff);
        attemptRepository.purgeOlderThan(logCutoff);
        auditOutboxRepository.purgeExpired(LocalDateTime.now());
        messageRepository.purgeOlderThan(messageCutoff);
    }
}
