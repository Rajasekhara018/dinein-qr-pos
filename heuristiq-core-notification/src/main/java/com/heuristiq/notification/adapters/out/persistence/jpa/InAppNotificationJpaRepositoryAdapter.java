package com.railcab.nms.adapters.out.persistence.jpa;

import com.railcab.nms.domain.repository.TenantScope;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.railcab.nms.adapters.out.persistence.PaginationMapper;
import com.railcab.nms.domain.model.InAppNotification;
import com.railcab.nms.domain.repository.IInAppNotificationRepository;
import com.railcab.nms.domain.repository.PageQuery;
import com.railcab.nms.domain.repository.PageResult;
import com.railcab.nms.adapters.out.persistence.jpa.InAppNotificationEntity;

@Repository
@ConditionalOnExpression("'${nms.persistence.type:mongo}' == 'mysql' or '${nms.persistence.type:mongo}' == 'postgres'")
class InAppNotificationJpaRepositoryAdapter implements IInAppNotificationRepository {

    private final InAppNotificationJpaSpringRepository springRepository;

    InAppNotificationJpaRepositoryAdapter(InAppNotificationJpaSpringRepository springRepository) {
        this.springRepository = springRepository;
    }

    @Override
    public InAppNotification save(InAppNotification notification) {
        return toDomain(springRepository.save(toEntity(notification)));
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

    // @Modifying bulk UPDATE queries need an active transaction to execute at all (Hibernate
    // throws TransactionRequiredException otherwise, not a silent no-op) -- neither this adapter
    // nor InAppNotificationService had one, so markAllAsRead 500'd on every call.
    @Override
    @Transactional
    public void markAsRead(String id, String userId, TenantScope scope) {
        if (scope.unrestricted()) {
            springRepository.markAsRead(id, userId);
        } else {
            springRepository.markAsReadScoped(id, userId, scope.tenantIdOrEmpty());
        }
    }

    @Override
    @Transactional
    public void markAllAsRead(String userId, TenantScope scope) {
        if (scope.unrestricted()) {
            springRepository.markAllAsRead(userId);
        } else {
            springRepository.markAllAsReadScoped(userId, scope.tenantIdOrEmpty());
        }
    }

    private InAppNotification toDomain(InAppNotificationEntity entity) {
        InAppNotification notification = new InAppNotification();
        notification.setId(entity.getId());
        notification.setTenantId(entity.getTenantId());
        notification.setUserId(entity.getUserId());
        notification.setTitle(entity.getTitle());
        notification.setMessage(entity.getMessage());
        notification.setRead(entity.isRead());
        notification.setCreatedAt(entity.getCreatedAt());
        return notification;
    }

    private InAppNotificationEntity toEntity(InAppNotification notification) {
        InAppNotificationEntity entity = new InAppNotificationEntity();
        entity.setId(notification.getId());
        entity.setTenantId(notification.getTenantId());
        entity.setUserId(notification.getUserId());
        entity.setTitle(notification.getTitle());
        entity.setMessage(notification.getMessage());
        entity.setRead(notification.isRead());
        entity.setCreatedAt(notification.getCreatedAt());
        return entity;
    }
}
