package com.heuristiq.identity.organization.subscription.repository.jpa;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.heuristiq.identity.organization.subscription.persistence.jpa.TenantApplicationEntity;

interface TenantApplicationJpaSpringRepository extends JpaRepository<TenantApplicationEntity, String> {
    Optional<TenantApplicationEntity> findByTenantIdAndApplicationCode(String tenantId, String applicationCode);

    List<TenantApplicationEntity> findByTenantId(String tenantId);

    List<TenantApplicationEntity> findByApplicationCode(String applicationCode);
}
