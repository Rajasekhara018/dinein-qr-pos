package com.railcab.nms.adapters.out.persistence.jpa;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Repository;

import com.railcab.nms.adapters.out.persistence.PaginationMapper;
import com.railcab.nms.domain.model.NotificationAttempt;
import com.railcab.nms.domain.repository.INotificationAttemptRepository;
import com.railcab.nms.domain.repository.PageQuery;
import com.railcab.nms.domain.repository.PageResult;

/** JPA (MySQL/Postgres) implementation of {@link INotificationAttemptRepository}. */
@Repository
@ConditionalOnExpression("'${nms.persistence.type:mongo}' == 'mysql' or '${nms.persistence.type:mongo}' == 'postgres'")
class NotificationAttemptJpaRepositoryAdapter implements INotificationAttemptRepository {

    private final NotificationAttemptJpaSpringRepository springRepository;

    NotificationAttemptJpaRepositoryAdapter(NotificationAttemptJpaSpringRepository springRepository) {
        this.springRepository = springRepository;
    }

    @Override
    public NotificationAttempt save(NotificationAttempt attempt) {
        return toDomain(springRepository.save(toEntity(attempt)));
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
        springRepository.deleteByStartedAtBefore(cutoff);
    }

    private NotificationAttempt toDomain(NotificationAttemptEntity e) {
        return NotificationAttempt.builder()
                .id(e.getId())
                .tenantId(e.getTenantId())
                .logId(e.getLogId())
                .messageId(e.getMessageId())
                .correlationId(e.getCorrelationId())
                .event(e.getEvent())
                .sourceService(e.getSourceService())
                .eventCode(e.getEventCode())
                .triggeringUserId(e.getTriggeringUserId())
                .entityType(e.getEntityType())
                .entityId(e.getEntityId())
                .channel(e.getChannel())
                .providerName(e.getProviderName())
                .providerMessageId(e.getProviderMessageId())
                .attemptNumber(e.getAttemptNumber())
                .status(e.getStatus())
                .providerStatus(e.getProviderStatus())
                .errorCode(e.getErrorCode())
                .errorMessage(e.getErrorMessage())
                .recipient(e.getRecipient())
                .latencyMs(e.getLatencyMs())
                .startedAt(e.getStartedAt())
                .completedAt(e.getCompletedAt())
                .statusUpdatedAt(e.getStatusUpdatedAt())
                .version(e.getVersion())
                .build();
    }

    private NotificationAttemptEntity toEntity(NotificationAttempt a) {
        return NotificationAttemptEntity.builder()
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
