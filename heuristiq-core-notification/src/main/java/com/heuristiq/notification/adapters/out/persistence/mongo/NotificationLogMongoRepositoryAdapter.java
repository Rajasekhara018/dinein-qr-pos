package com.heuristiq.notification.adapters.out.persistence.mongo;

import com.heuristiq.notification.domain.repository.TenantScope;
import java.time.LocalDateTime;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import com.heuristiq.notification.adapters.out.persistence.PaginationMapper;
import com.heuristiq.notification.domain.model.NotificationLog;
import com.heuristiq.notification.domain.repository.INotificationLogRepository;
import com.heuristiq.notification.domain.repository.PageQuery;
import com.heuristiq.notification.domain.repository.PageResult;
import com.heuristiq.notification.domain.model.NotificationChannel;
import com.heuristiq.notification.adapters.out.persistence.mongo.NotificationLogDocument;

@Repository
@ConditionalOnProperty(prefix = "nms.persistence", name = "type", havingValue = "mongo", matchIfMissing = true)
class NotificationLogMongoRepositoryAdapter implements INotificationLogRepository {

    private final NotificationLogMongoSpringRepository springRepository;

    NotificationLogMongoRepositoryAdapter(NotificationLogMongoSpringRepository springRepository) {
        this.springRepository = springRepository;
    }

    @Override
    public NotificationLog save(NotificationLog log) {
        return toDomain(springRepository.save(toDocument(log)));
    }

    @Override
    public java.util.Optional<NotificationLog> findById(String id) {
        return springRepository.findById(id).map(this::toDomain);
    }

    @Override
    public PageResult<NotificationLog> findAll(PageQuery pageQuery) {
        return PaginationMapper.toPageResult(
                (pageQuery.scope().unrestricted()
                        ? springRepository.findAll(PaginationMapper.toPageable(pageQuery))
                        : springRepository.findAllScoped(pageQuery.scope().tenantIdOrEmpty(), PaginationMapper.toPageable(pageQuery))).map(this::toDomain));
    }

    @Override
    public PageResult<NotificationLog> findByEvent(String event, PageQuery pageQuery) {
        return PaginationMapper.toPageResult(
                (pageQuery.scope().unrestricted()
                        ? springRepository.findByEvent(event, PaginationMapper.toPageable(pageQuery))
                        : springRepository.findByEventScoped(event, pageQuery.scope().tenantIdOrEmpty(), PaginationMapper.toPageable(pageQuery))).map(this::toDomain));
    }

    @Override
    public PageResult<NotificationLog> findByCorrelationId(String correlationId, PageQuery pageQuery) {
        return PaginationMapper.toPageResult(
                (pageQuery.scope().unrestricted()
                        ? springRepository.findByCorrelationId(correlationId, PaginationMapper.toPageable(pageQuery))
                        : springRepository.findByCorrelationIdScoped(correlationId, pageQuery.scope().tenantIdOrEmpty(), PaginationMapper.toPageable(pageQuery))).map(this::toDomain));
    }

    @Override
    public PageResult<NotificationLog> findByChannel(NotificationChannel channel, PageQuery pageQuery) {
        return PaginationMapper.toPageResult(
                (pageQuery.scope().unrestricted()
                        ? springRepository.findByChannel(channel, PaginationMapper.toPageable(pageQuery))
                        : springRepository.findByChannelScoped(channel, pageQuery.scope().tenantIdOrEmpty(), PaginationMapper.toPageable(pageQuery))).map(this::toDomain));
    }

    @Override
    public PageResult<NotificationLog> findBySentAtBetween(LocalDateTime from, LocalDateTime to, PageQuery pageQuery) {
        return PaginationMapper.toPageResult(
                (pageQuery.scope().unrestricted()
                        ? springRepository.findBySentAtBetween(from, to, PaginationMapper.toPageable(pageQuery))
                        : springRepository.findBySentAtBetweenScoped(from, to, pageQuery.scope().tenantIdOrEmpty(), PaginationMapper.toPageable(pageQuery))).map(this::toDomain));
    }

    @Override
    public long countByChannelAndSuccessTrue(NotificationChannel channel) {
        return springRepository.countByChannelAndSuccessTrue(channel);
    }

    @Override
    public long countByChannelAndSuccessFalse(NotificationChannel channel) {
        return springRepository.countByChannelAndSuccessFalse(channel);
    }

    @Override
    public long countByChannelAndSuccess(NotificationChannel channel, boolean success, TenantScope scope) {
        if (scope.unrestricted()) {
            return success ? springRepository.countByChannelAndSuccessTrue(channel)
                    : springRepository.countByChannelAndSuccessFalse(channel);
        }
        return springRepository.countByChannelAndSuccessScoped(channel, success, scope.tenantIdOrEmpty());
    }

    @Override
    public void purgeOlderThan(LocalDateTime cutoff) {
        // No-op: the "sentAt" TTL index (see NotificationLogIndexConfig) already expires old
        // records natively on the Mongo side.
    }

    private NotificationLog toDomain(NotificationLogDocument doc) {
        return NotificationLog.builder()
                .id(doc.getId())
                .tenantId(doc.getTenantId())
                .messageId(doc.getMessageId())
                .correlationId(doc.getCorrelationId())
                .event(doc.getEvent())
                .sourceService(doc.getSourceService())
                .eventCode(doc.getEventCode())
                .triggeringUserId(doc.getTriggeringUserId())
                .entityType(doc.getEntityType())
                .entityId(doc.getEntityId())
                .channel(doc.getChannel())
                .providerName(doc.getProviderName())
                .providerMessageId(doc.getProviderMessageId())
                .status(doc.getStatus())
                .recipient(doc.getRecipient())
                .success(doc.isSuccess())
                .retryCount(doc.getRetryCount())
                .errorMessage(doc.getErrorMessage())
                .renderedBody(doc.getRenderedBody())
                .sentAt(doc.getSentAt())
                .build();
    }

    private NotificationLogDocument toDocument(NotificationLog log) {
        return NotificationLogDocument.builder()
                .id(log.getId())
                .tenantId(log.getTenantId())
                .messageId(log.getMessageId())
                .correlationId(log.getCorrelationId())
                .event(log.getEvent())
                .sourceService(log.getSourceService())
                .eventCode(log.getEventCode())
                .triggeringUserId(log.getTriggeringUserId())
                .entityType(log.getEntityType())
                .entityId(log.getEntityId())
                .channel(log.getChannel())
                .providerName(log.getProviderName())
                .providerMessageId(log.getProviderMessageId())
                .status(log.getStatus())
                .recipient(log.getRecipient())
                .success(log.isSuccess())
                .retryCount(log.getRetryCount())
                .errorMessage(log.getErrorMessage())
                .renderedBody(log.getRenderedBody())
                .sentAt(log.getSentAt())
                .build();
    }
}
