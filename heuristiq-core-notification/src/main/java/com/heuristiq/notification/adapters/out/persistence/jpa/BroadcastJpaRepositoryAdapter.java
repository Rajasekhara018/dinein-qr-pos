package com.heuristiq.notification.adapters.out.persistence.jpa;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Repository;

import com.heuristiq.notification.adapters.out.persistence.PaginationMapper;
import com.heuristiq.notification.domain.model.Broadcast;
import com.heuristiq.notification.domain.model.BroadcastStatus;
import com.heuristiq.notification.domain.repository.IBroadcastRepository;
import com.heuristiq.notification.domain.repository.PageQuery;
import com.heuristiq.notification.domain.repository.PageResult;

@Repository
// Same JPA/Hibernate code path serves either relational backend — only the datasource
// URL/driver/dialect (application-sql.properties vs application-postgres.properties) differ.
@ConditionalOnExpression("'${nms.persistence.type:mongo}' == 'mysql' or '${nms.persistence.type:mongo}' == 'postgres'")
class BroadcastJpaRepositoryAdapter implements IBroadcastRepository {

    private final BroadcastJpaSpringRepository springRepository;

    BroadcastJpaRepositoryAdapter(BroadcastJpaSpringRepository springRepository) {
        this.springRepository = springRepository;
    }

    @Override
    public Broadcast save(Broadcast broadcast) {
        return toDomain(springRepository.save(toEntity(broadcast)));
    }

    @Override
    public Optional<Broadcast> findById(String id) {
        return springRepository.findById(id).map(BroadcastJpaRepositoryAdapter::toDomain);
    }

    @Override
    public PageResult<Broadcast> findAll(PageQuery pageQuery) {
        return PaginationMapper.toPageResult(
                (pageQuery.scope().unrestricted()
                        ? springRepository.findAllByOrderByCreatedAtDesc(PaginationMapper.toPageable(pageQuery))
                        : springRepository.findAllScoped(pageQuery.scope().tenantIdOrEmpty(), PaginationMapper.toPageable(pageQuery)))
                        .map(BroadcastJpaRepositoryAdapter::toDomain));
    }

    @Override
    public List<Broadcast> findDue(BroadcastStatus status, LocalDateTime notAfter) {
        return springRepository.findDue(status, notAfter).stream()
                .map(BroadcastJpaRepositoryAdapter::toDomain)
                .toList();
    }

    private static BroadcastEntity toEntity(Broadcast b) {
        return BroadcastEntity.builder()
                .id(b.getId())
                .tenantId(b.getTenantId())
                .title(b.getTitle())
                .message(b.getMessage())
                .city(b.getCity())
                .event(b.getEvent())
                .variables(b.getVariables())
                .segment(b.getSegment())
                .channels(b.getChannels())
                .scheduledAt(b.getScheduledAt())
                .status(b.getStatus())
                .recipientCount(b.getRecipientCount())
                .targetedCount(b.getTargetedCount())
                .failedCount(b.getFailedCount())
                .createdBy(b.getCreatedBy())
                .createdAt(b.getCreatedAt())
                .updatedAt(b.getUpdatedAt())
                .sentAt(b.getSentAt())
                .build();
    }

    private static Broadcast toDomain(BroadcastEntity entity) {
        return Broadcast.builder()
                .id(entity.getId())
                .tenantId(entity.getTenantId())
                .title(entity.getTitle())
                .message(entity.getMessage())
                .city(entity.getCity())
                .event(entity.getEvent())
                .variables(entity.getVariables())
                .segment(entity.getSegment())
                .channels(entity.getChannels())
                .scheduledAt(entity.getScheduledAt())
                .status(entity.getStatus())
                .recipientCount(entity.getRecipientCount())
                .targetedCount(entity.getTargetedCount())
                .failedCount(entity.getFailedCount())
                .createdBy(entity.getCreatedBy())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .sentAt(entity.getSentAt())
                .build();
    }
}
