package com.railcab.nms.adapters.out.persistence.mongo;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import com.railcab.nms.adapters.out.persistence.PaginationMapper;
import com.railcab.nms.domain.model.NotificationMessage;
import com.railcab.nms.domain.repository.INotificationMessageRepository;
import com.railcab.nms.domain.repository.PageQuery;
import com.railcab.nms.domain.repository.PageResult;
import com.railcab.nms.domain.model.NotificationMessageStatus;
import com.railcab.nms.adapters.out.persistence.mongo.NotificationMessageDocument;

@Repository
@ConditionalOnProperty(prefix = "nms.persistence", name = "type", havingValue = "mongo", matchIfMissing = true)
class NotificationMessageMongoRepositoryAdapter implements INotificationMessageRepository {

    private final NotificationMessageMongoSpringRepository springRepository;

    NotificationMessageMongoRepositoryAdapter(NotificationMessageMongoSpringRepository springRepository) {
        this.springRepository = springRepository;
    }

    @Override
    public NotificationMessage insert(NotificationMessage message) {
        return toDomain(springRepository.insert(toDocument(message)));
    }

    @Override
    public NotificationMessage save(NotificationMessage message) {
        return toDomain(springRepository.save(toDocument(message)));
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
        // No-op: the "completedAt" TTL index (see NotificationMessageIndexConfig) already
        // expires old records natively on the Mongo side.
    }

    private NotificationMessage toDomain(NotificationMessageDocument doc) {
        NotificationMessage message = new NotificationMessage();
        message.setId(doc.getId());
        message.setTenantId(doc.getTenantId());
        message.setMessageId(doc.getMessageId());
        message.setCorrelationId(doc.getCorrelationId());
        message.setEvent(doc.getEvent());
        message.setSourceService(doc.getSourceService());
        message.setEventCode(doc.getEventCode());
        message.setTriggeringUserId(doc.getTriggeringUserId());
        message.setEntityType(doc.getEntityType());
        message.setEntityId(doc.getEntityId());
        message.setStatus(doc.getStatus());
        message.setErrorMessage(doc.getErrorMessage());
        message.setCreatedAt(doc.getCreatedAt());
        message.setUpdatedAt(doc.getUpdatedAt());
        message.setCompletedAt(doc.getCompletedAt());
        return message;
    }

    private NotificationMessageDocument toDocument(NotificationMessage message) {
        NotificationMessageDocument doc = new NotificationMessageDocument();
        doc.setId(message.getId());
        doc.setTenantId(message.getTenantId());
        doc.setMessageId(message.getMessageId());
        doc.setCorrelationId(message.getCorrelationId());
        doc.setEvent(message.getEvent());
        doc.setSourceService(message.getSourceService());
        doc.setEventCode(message.getEventCode());
        doc.setTriggeringUserId(message.getTriggeringUserId());
        doc.setEntityType(message.getEntityType());
        doc.setEntityId(message.getEntityId());
        doc.setStatus(message.getStatus());
        doc.setErrorMessage(message.getErrorMessage());
        doc.setCreatedAt(message.getCreatedAt());
        doc.setUpdatedAt(message.getUpdatedAt());
        doc.setCompletedAt(message.getCompletedAt());
        return doc;
    }
}
