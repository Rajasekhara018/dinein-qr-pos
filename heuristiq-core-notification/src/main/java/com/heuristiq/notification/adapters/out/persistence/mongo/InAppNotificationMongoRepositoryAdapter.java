package com.railcab.nms.adapters.out.persistence.mongo;

import com.railcab.nms.domain.repository.TenantScope;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import com.railcab.nms.adapters.out.persistence.PaginationMapper;
import com.railcab.nms.domain.model.InAppNotification;
import com.railcab.nms.domain.repository.IInAppNotificationRepository;
import com.railcab.nms.domain.repository.PageQuery;
import com.railcab.nms.domain.repository.PageResult;
import com.railcab.nms.adapters.out.persistence.mongo.InAppNotificationDocument;

@Repository
@ConditionalOnProperty(prefix = "nms.persistence", name = "type", havingValue = "mongo", matchIfMissing = true)
class InAppNotificationMongoRepositoryAdapter implements IInAppNotificationRepository {

    private final InAppNotificationMongoSpringRepository springRepository;

    InAppNotificationMongoRepositoryAdapter(InAppNotificationMongoSpringRepository springRepository) {
        this.springRepository = springRepository;
    }

    @Override
    public InAppNotification save(InAppNotification notification) {
        return toDomain(springRepository.save(toDocument(notification)));
    }

    @Override
    public PageResult<InAppNotification> findByUserId(String userId, PageQuery pageQuery) {
        return PaginationMapper.toPageResult(
                (pageQuery.scope().unrestricted()
                        ? springRepository.findByUserIdOrderByCreatedAtDesc(userId, PaginationMapper.toPageable(pageQuery))
                        : springRepository.findByUserIdScoped(userId, pageQuery.scope().tenantIdOrEmpty(), PaginationMapper.toPageable(pageQuery)))
                        .map(this::toDomain));
    }

    @Override
    public PageResult<InAppNotification> findUnreadByUserId(String userId, PageQuery pageQuery) {
        return PaginationMapper.toPageResult(
                (pageQuery.scope().unrestricted()
                        ? springRepository.findByUserIdAndReadFalseOrderByCreatedAtDesc(userId, PaginationMapper.toPageable(pageQuery))
                        : springRepository.findUnreadByUserIdScoped(userId, pageQuery.scope().tenantIdOrEmpty(), PaginationMapper.toPageable(pageQuery)))
                        .map(this::toDomain));
    }

    @Override
    public long countUnreadByUserId(String userId, TenantScope scope) {
        return scope.unrestricted()
                ? springRepository.countByUserIdAndReadFalse(userId)
                : springRepository.countUnreadByUserIdScoped(userId, scope.tenantIdOrEmpty());
    }

    @Override
    public void markAsRead(String id, String userId, TenantScope scope) {
        springRepository.findById(id)
                .filter(doc -> userId.equals(doc.getUserId()))
                .filter(doc -> scope.allows(doc.getTenantId()))
                .ifPresent(doc -> {
                    doc.setRead(true);
                    springRepository.save(doc);
                });
    }

    @Override
    public void markAllAsRead(String userId, TenantScope scope) {
        var unread = scope.unrestricted()
                ? springRepository.findByUserIdAndReadFalse(userId)
                : springRepository.findUnreadListByUserIdScoped(userId, scope.tenantIdOrEmpty());
        unread.forEach(doc -> doc.setRead(true));
        springRepository.saveAll(unread);
    }

    private InAppNotification toDomain(InAppNotificationDocument doc) {
        InAppNotification notification = new InAppNotification();
        notification.setId(doc.getId());
        notification.setTenantId(doc.getTenantId());
        notification.setUserId(doc.getUserId());
        notification.setTitle(doc.getTitle());
        notification.setMessage(doc.getMessage());
        notification.setRead(doc.isRead());
        notification.setCreatedAt(doc.getCreatedAt());
        return notification;
    }

    private InAppNotificationDocument toDocument(InAppNotification notification) {
        InAppNotificationDocument doc = new InAppNotificationDocument();
        doc.setId(notification.getId());
        doc.setTenantId(notification.getTenantId());
        doc.setUserId(notification.getUserId());
        doc.setTitle(notification.getTitle());
        doc.setMessage(notification.getMessage());
        doc.setRead(notification.isRead());
        doc.setCreatedAt(notification.getCreatedAt());
        return doc;
    }
}
