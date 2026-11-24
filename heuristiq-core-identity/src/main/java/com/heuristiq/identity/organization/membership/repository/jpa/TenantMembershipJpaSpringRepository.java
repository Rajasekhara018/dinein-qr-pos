package com.heuristiq.uam.organization.membership.repository.jpa;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.heuristiq.uam.organization.membership.TenantMembershipStatus;
import com.heuristiq.uam.organization.membership.persistence.jpa.TenantMembershipEntity;

interface TenantMembershipJpaSpringRepository extends JpaRepository<TenantMembershipEntity, String> {
    Optional<TenantMembershipEntity> findByTenantIdAndUserId(String tenantId, String userId);

    List<TenantMembershipEntity> findByUserId(String userId);

    Page<TenantMembershipEntity> findByTenantId(String tenantId, Pageable pageable);

    long countByTenantIdAndStatus(String tenantId, TenantMembershipStatus status);
}
