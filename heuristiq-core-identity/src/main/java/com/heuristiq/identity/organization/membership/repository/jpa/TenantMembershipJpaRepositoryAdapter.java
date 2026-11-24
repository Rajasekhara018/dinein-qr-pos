package com.heuristiq.uam.organization.membership.repository.jpa;

import java.util.List;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import com.heuristiq.uam.organization.membership.TenantMembership;
import com.heuristiq.uam.organization.membership.TenantMembershipStatus;
import com.heuristiq.uam.organization.membership.persistence.jpa.TenantMembershipEntity;
import com.heuristiq.uam.organization.membership.repository.TenantMembershipRepository;

@Repository
@ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "postgres")
class TenantMembershipJpaRepositoryAdapter implements TenantMembershipRepository {
    private final TenantMembershipJpaSpringRepository springRepository;

    TenantMembershipJpaRepositoryAdapter(TenantMembershipJpaSpringRepository springRepository) {
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

    private TenantMembershipEntity toStored(TenantMembership membership) {
        return TenantMembershipEntity.builder()
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

    private TenantMembership toDomain(TenantMembershipEntity stored) {
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
