package com.railcab.nms.adapters.out.persistence.jpa;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Repository;

import com.railcab.nms.adapters.out.persistence.PaginationMapper;
import com.railcab.nms.domain.model.NotificationMessage;
import com.railcab.nms.domain.repository.INotificationMessageRepository;
import com.railcab.nms.domain.repository.PageQuery;
import com.railcab.nms.domain.repository.PageResult;
import com.railcab.nms.domain.model.NotificationMessageStatus;
import com.railcab.nms.adapters.out.persistence.jpa.NotificationMessageEntity;

@Repository
@ConditionalOnExpression("'${nms.persistence.type:mongo}' == 'mysql' or '${nms.persistence.type:mongo}' == 'postgres'")
class NotificationMessageJpaRepositoryAdapter implements INotificationMessageRepository {

    private final NotificationMessageJpaSpringRepository springRepository;

    NotificationMessageJpaRepositoryAdapter(NotificationMessageJpaSpringRepository springRepository) {
        this.springRepository = springRepository;
    }

    @Override
    public NotificationMessage insert(NotificationMessage message) {
        // saveAndFlush forces the unique-constraint check on message_id synchronously, so a
        // conflict surfaces as DataIntegrityViolationException from this call -- matching the
        // Mongo adapter's insert() semantics that NotificationMessageService.reserve() relies on.
        return toDomain(springRepository.saveAndFlush(toEntity(message)));
    }

    @Override
    public NotificationMessage save(NotificationMessage message) {
        return toDomain(springRepository.save(toEntity(message)));
    }

    @Override
    public Optional<NotificationMessage> findByMessageId(String messageId) {
        return springRepository.findByMessageId(messageId).map(this::toDomain);
    }

    @Override
    public Optional<NotificationMessage> findFirstByMessageIdAndStatusAndUpdatedAtBefore(
            String messageId, NotificationMessageStatus status, LocalDateTime updatedBefore) {
        return springRepository.findFirstByMessageIdAndStatusAndUpdatedAtBefore(messageId, status, updatedBefore)
                .map(this::toDomain);
    }

    @Override
    public PageResult<NotificationMessage> findAll(PageQuery pageQuery) {
        return PaginationMapper.toPageResult(
                (pageQuery.scope().unrestricted()
                        ? springRepository.findAll(PaginationMapper.toPageable(pageQuery))
                        : springRepository.findAllScoped(pageQuery.scope().tenantIdOrEmpty(), PaginationMapper.toPageable(pageQuery))).map(this::toDomain));
    }

    @Override
    public PageResult<NotificationMessage> findByCorrelationId(String correlationId, PageQuery pageQuery) {
        return PaginationMapper.toPageResult(
                (pageQuery.scope().unrestricted()
                        ? springRepository.findByCorrelationId(correlationId, PaginationMapper.toPageable(pageQuery))
                        : springRepository.findByCorrelationIdScoped(correlationId, pageQuery.scope().tenantIdOrEmpty(), PaginationMapper.toPageable(pageQuery))).map(this::toDomain));
    }

    @Override
    public PageResult<NotificationMessage> findByStatus(NotificationMessageStatus status, PageQuery pageQuery) {
        return PaginationMapper.toPageResult(
                (pageQuery.scope().unrestricted()
                        ? springRepository.findByStatus(status, PaginationMapper.toPageable(pageQuery))
                        : springRepository.findByStatusScoped(status, pageQuery.scope().tenantIdOrEmpty(), PaginationMapper.toPageable(pageQuery))).map(this::toDomain));
    }

    @Override
    public void purgeOlderThan(LocalDateTime cutoff) {
        springRepository.deleteByCompletedAtBefore(cutoff);
    }

    private NotificationMessage toDomain(NotificationMessageEntity entity) {
        NotificationMessage message = new NotificationMessage();
        message.setId(entity.getId());
        message.setTenantId(entity.getTenantId());
        message.setMessageId(entity.getMessageId());
        message.setCorrelationId(entity.getCorrelationId());
        message.setEvent(entity.getEvent());
        message.setSourceService(entity.getSourceService());
        message.setEventCode(entity.getEventCode());
        message.setTriggeringUserId(entity.getTriggeringUserId());
        message.setEntityType(entity.getEntityType());
        message.setEntityId(entity.getEntityId());
        message.setStatus(entity.getStatus());
        message.setErrorMessage(entity.getErrorMessage());
        message.setCreatedAt(entity.getCreatedAt());
        message.setUpdatedAt(entity.getUpdatedAt());
        message.setCompletedAt(entity.getCompletedAt());
        return message;
    }

    private NotificationMessageEntity toEntity(NotificationMessage message) {
        NotificationMessageEntity entity = new NotificationMessageEntity();
        entity.setId(message.getId());
        entity.setTenantId(message.getTenantId());
        entity.setMessageId(message.getMessageId());
        entity.setCorrelationId(message.getCorrelationId());
        entity.setEvent(message.getEvent());
        entity.setSourceService(message.getSourceService());
        entity.setEventCode(message.getEventCode());
        entity.setTriggeringUserId(message.getTriggeringUserId());
        entity.setEntityType(message.getEntityType());
        entity.setEntityId(message.getEntityId());
        entity.setStatus(message.getStatus());
        entity.setErrorMessage(message.getErrorMessage());
        entity.setCreatedAt(message.getCreatedAt());
        entity.setUpdatedAt(message.getUpdatedAt());
        entity.setCompletedAt(message.getCompletedAt());
        return entity;
    }
}
