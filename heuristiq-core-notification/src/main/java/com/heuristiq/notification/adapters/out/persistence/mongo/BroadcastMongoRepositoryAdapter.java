package com.railcab.nms.adapters.out.persistence.mongo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import com.railcab.nms.adapters.out.persistence.PaginationMapper;
import com.railcab.nms.domain.model.Broadcast;
import com.railcab.nms.domain.repository.IBroadcastRepository;
import com.railcab.nms.domain.repository.PageQuery;
import com.railcab.nms.domain.repository.PageResult;
import com.railcab.nms.domain.model.BroadcastStatus;
import com.railcab.nms.adapters.out.persistence.mongo.BroadcastDocument;

@Repository
@ConditionalOnProperty(prefix = "nms.persistence", name = "type", havingValue = "mongo", matchIfMissing = true)
class BroadcastMongoRepositoryAdapter implements IBroadcastRepository {

    private final BroadcastMongoSpringRepository springRepository;

    BroadcastMongoRepositoryAdapter(BroadcastMongoSpringRepository springRepository) {
        this.springRepository = springRepository;
    }

    @Override
    public Broadcast save(Broadcast broadcast) {
        BroadcastDocument doc = toDocument(broadcast);
        BroadcastDocument saved = springRepository.save(doc);
        return toDomain(saved);
    }

    @Override
    public Optional<Broadcast> findById(String id) {
        return springRepository.findById(id).map(BroadcastMongoRepositoryAdapter::toDomain);
    }

    @Override
    public PageResult<Broadcast> findAll(PageQuery pageQuery) {
        return PaginationMapper.toPageResult(
                (pageQuery.scope().unrestricted()
                        ? springRepository.findAllByOrderByCreatedAtDesc(PaginationMapper.toPageable(pageQuery))
                        : springRepository.findAllScoped(pageQuery.scope().tenantIdOrEmpty(), PaginationMapper.toPageable(pageQuery)))
                        .map(BroadcastMongoRepositoryAdapter::toDomain));
    }

    @Override
    public List<Broadcast> findDue(BroadcastStatus status, LocalDateTime notAfter) {
        return springRepository.findDue(status, notAfter).stream()
                .map(BroadcastMongoRepositoryAdapter::toDomain)
                .toList();
    }

    private static BroadcastDocument toDocument(Broadcast b) {
        return BroadcastDocument.builder()
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

    private static Broadcast toDomain(BroadcastDocument doc) {
        return Broadcast.builder()
                .id(doc.getId())
                .tenantId(doc.getTenantId())
                .title(doc.getTitle())
                .message(doc.getMessage())
                .city(doc.getCity())
                .event(doc.getEvent())
                .variables(doc.getVariables())
                .segment(doc.getSegment())
                .channels(doc.getChannels())
                .scheduledAt(doc.getScheduledAt())
                .status(doc.getStatus())
                .recipientCount(doc.getRecipientCount())
                .targetedCount(doc.getTargetedCount())
                .failedCount(doc.getFailedCount())
                .createdBy(doc.getCreatedBy())
                .createdAt(doc.getCreatedAt())
                .updatedAt(doc.getUpdatedAt())
                .sentAt(doc.getSentAt())
                .build();
    }
}
