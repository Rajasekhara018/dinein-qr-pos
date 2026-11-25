package com.heuristiq.identity.organization.membership.repository.mongo;

import java.util.List;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import com.heuristiq.identity.organization.membership.TenantMembership;
import com.heuristiq.identity.organization.membership.TenantMembershipStatus;
import com.heuristiq.identity.organization.membership.persistence.mongo.TenantMembershipDocument;
import com.heuristiq.identity.organization.membership.repository.TenantMembershipRepository;

@Repository
@ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "mongo", matchIfMissing = true)
class TenantMembershipMongoRepositoryAdapter implements TenantMembershipRepository {
    private final TenantMembershipMongoSpringRepository springRepository;

    TenantMembershipMongoRepositoryAdapter(TenantMembershipMongoSpringRepository springRepository) {
        this.springRepository = springRepository;
    }

    @Override
    public TenantMembership save(TenantMembership membership) {
        return toDomain(springRepository.save(toStored(membership)));
    }

    @Override
    public Optional<TenantMembership> findByTenantIdAndUserId(String tenantId, String userId) {
        return springRepository.findByTenantIdAndUserId(tenantId, userId).map(this::toDomain);
    }

    @Override
    public List<TenantMembership> findByUserId(String userId) {
        return springRepository.findByUserId(userId).stream().map(this::toDomain).toList();
    }

    @Override
    public Page<TenantMembership> findByTenantId(String tenantId, Pageable pageable) {
        return springRepository.findByTenantId(tenantId, pageable).map(this::toDomain);
    }

    @Override
    public long countByTenantIdAndStatus(String tenantId, TenantMembershipStatus status) {
        return springRepository.countByTenantIdAndStatus(tenantId, status);
    }

    private TenantMembershipDocument toStored(TenantMembership membership) {
        return TenantMembershipDocument.builder()
                .id(membership.getId())
                .tenantId(membership.getTenantId())
                .userId(membership.getUserId())
                .status(membership.getStatus())
                .employeeId(membership.getEmployeeId())
                .addedBy(membership.getAddedBy())
                .joinedAt(membership.getJoinedAt())
                .updatedBy(membership.getUpdatedBy())
                .updatedAt(membership.getUpdatedAt())
                .build();
    }

    private TenantMembership toDomain(TenantMembershipDocument stored) {
        return TenantMembership.builder()
                .id(stored.getId())
                .tenantId(stored.getTenantId())
                .userId(stored.getUserId())
                .status(stored.getStatus())
                .employeeId(stored.getEmployeeId())
                .addedBy(stored.getAddedBy())
                .joinedAt(stored.getJoinedAt())
                .updatedBy(stored.getUpdatedBy())
                .updatedAt(stored.getUpdatedAt())
                .build();
    }
}
