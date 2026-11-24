package com.railcab.nms.adapters.out.persistence.jpa;

import com.railcab.nms.domain.repository.TenantScope;
import java.time.LocalDateTime;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Repository;

import com.railcab.nms.adapters.out.persistence.PaginationMapper;
import com.railcab.nms.domain.model.NotificationLog;
import com.railcab.nms.domain.repository.INotificationLogRepository;
import com.railcab.nms.domain.repository.PageQuery;
import com.railcab.nms.domain.repository.PageResult;
import com.railcab.nms.domain.model.NotificationChannel;
import com.railcab.nms.adapters.out.persistence.jpa.NotificationLogEntity;

@Repository
@ConditionalOnExpression("'${nms.persistence.type:mongo}' == 'mysql' or '${nms.persistence.type:mongo}' == 'postgres'")
class NotificationLogJpaRepositoryAdapter implements INotificationLogRepository {

    private final NotificationLogJpaSpringRepository springRepository;

    NotificationLogJpaRepositoryAdapter(NotificationLogJpaSpringRepository springRepository) {
        this.springRepository = springRepository;
    }

    @Override
    public NotificationLog save(NotificationLog log) {
        return toDomain(springRepository.save(toEntity(log)));
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
        springRepository.deleteBySentAtBefore(cutoff);
    }

    private NotificationLog toDomain(NotificationLogEntity entity) {
        return NotificationLog.builder()
                .id(entity.getId())
                .tenantId(entity.getTenantId())
                .messageId(entity.getMessageId())
                .correlationId(entity.getCorrelationId())
                .event(entity.getEvent())
                .sourceService(entity.getSourceService())
                .eventCode(entity.getEventCode())
                .triggeringUserId(entity.getTriggeringUserId())
                .entityType(entity.getEntityType())
                .entityId(entity.getEntityId())
                .channel(entity.getChannel())
                .providerName(entity.getProviderName())
                .providerMessageId(entity.getProviderMessageId())
                .status(entity.getStatus())
                .recipient(entity.getRecipient())
                .success(entity.isSuccess())
                .retryCount(entity.getRetryCount())
                .errorMessage(entity.getErrorMessage())
                .renderedBody(entity.getRenderedBody())
                .sentAt(entity.getSentAt())
                .build();
    }

    private NotificationLogEntity toEntity(NotificationLog log) {
        return NotificationLogEntity.builder()
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
