package com.heuristiq.notification.adapters.out.persistence.mongo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import com.heuristiq.notification.adapters.out.persistence.PaginationMapper;
import com.heuristiq.notification.domain.model.NotificationAttempt;
import com.heuristiq.notification.domain.repository.INotificationAttemptRepository;
import com.heuristiq.notification.domain.repository.PageQuery;
import com.heuristiq.notification.domain.repository.PageResult;

/** Mongo implementation of {@link INotificationAttemptRepository}. */
@Repository
@ConditionalOnProperty(prefix = "nms.persistence", name = "type", havingValue = "mongo", matchIfMissing = true)
class NotificationAttemptMongoRepositoryAdapter implements INotificationAttemptRepository {

    private final NotificationAttemptMongoSpringRepository springRepository;

    NotificationAttemptMongoRepositoryAdapter(NotificationAttemptMongoSpringRepository springRepository) {
        this.springRepository = springRepository;
    }

    @Override
    public NotificationAttempt save(NotificationAttempt attempt) {
        return toDomain(springRepository.save(toDocument(attempt)));
    }

    @Override
    public Optional<NotificationAttempt> findById(String id) {
        return springRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<NotificationAttempt> findLatestByProviderMessageId(String providerMessageId) {
        return springRepository.findFirstByProviderMessageIdOrderByStartedAtDesc(providerMessageId).map(this::toDomain);
    }

    @Override
    public PageResult<NotificationAttempt> findByMessageId(String messageId, PageQuery pageQuery) {
        return PaginationMapper.toPageResult((pageQuery.scope().unrestricted()
                        ? springRepository.findByMessageIdOrderByStartedAtAscAttemptNumberAsc(messageId, PaginationMapper.toPageable(pageQuery))
                        : springRepository.findByMessageIdScoped(messageId, pageQuery.scope().tenantIdOrEmpty(), PaginationMapper.toPageable(pageQuery)))
                .map(this::toDomain));
    }

    @Override
    public PageResult<NotificationAttempt> findByCorrelationId(String correlationId, PageQuery pageQuery) {
        return PaginationMapper.toPageResult((pageQuery.scope().unrestricted()
                        ? springRepository.findByCorrelationIdOrderByStartedAtAscAttemptNumberAsc(correlationId, PaginationMapper.toPageable(pageQuery))
                        : springRepository.findByCorrelationIdScoped(correlationId, pageQuery.scope().tenantIdOrEmpty(), PaginationMapper.toPageable(pageQuery)))
                .map(this::toDomain));
    }

    @Override
    public List<NotificationAttempt> findByLogId(String logId) {
        return springRepository.findByLogIdOrderByAttemptNumberAsc(logId).stream().map(this::toDomain).toList();
    }

    @Override
    public void purgeOlderThan(LocalDateTime cutoff) {
        // No-op: the "startedAt" TTL index (see NotificationAttemptIndexConfig) already expires old
        // records natively on the Mongo side.
    }

    private NotificationAttempt toDomain(NotificationAttemptDocument d) {
        return NotificationAttempt.builder()
                .id(d.getId())
                .tenantId(d.getTenantId())
                .logId(d.getLogId())
                .messageId(d.getMessageId())
                .correlationId(d.getCorrelationId())
                .event(d.getEvent())
                .sourceService(d.getSourceService())
                .eventCode(d.getEventCode())
                .triggeringUserId(d.getTriggeringUserId())
                .entityType(d.getEntityType())
                .entityId(d.getEntityId())
                .channel(d.getChannel())
                .providerName(d.getProviderName())
                .providerMessageId(d.getProviderMessageId())
                .attemptNumber(d.getAttemptNumber())
                .status(d.getStatus())
                .providerStatus(d.getProviderStatus())
                .errorCode(d.getErrorCode())
                .errorMessage(d.getErrorMessage())
                .recipient(d.getRecipient())
                .latencyMs(d.getLatencyMs())
                .startedAt(d.getStartedAt())
                .completedAt(d.getCompletedAt())
                .statusUpdatedAt(d.getStatusUpdatedAt())
                .version(d.getVersion())
                .build();
    }

    private NotificationAttemptDocument toDocument(NotificationAttempt a) {
        return NotificationAttemptDocument.builder()
                .id(a.getId())
                .tenantId(a.getTenantId())
                .logId(a.getLogId())
                .messageId(a.getMessageId())
                .correlationId(a.getCorrelationId())
                .event(a.getEvent())
                .sourceService(a.getSourceService())
                .eventCode(a.getEventCode())
                .triggeringUserId(a.getTriggeringUserId())
                .entityType(a.getEntityType())
                .entityId(a.getEntityId())
                .channel(a.getChannel())
                .providerName(a.getProviderName())
                .providerMessageId(a.getProviderMessageId())
                .attemptNumber(a.getAttemptNumber())
                .status(a.getStatus())
                .providerStatus(a.getProviderStatus())
                .errorCode(a.getErrorCode())
                .errorMessage(a.getErrorMessage())
                .recipient(a.getRecipient())
                .latencyMs(a.getLatencyMs())
                .startedAt(a.getStartedAt())
                .completedAt(a.getCompletedAt())
                .statusUpdatedAt(a.getStatusUpdatedAt())
                .version(a.getVersion())
                .build();
    }
}
