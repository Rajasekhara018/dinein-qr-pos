package com.heuristiq.uam.application;

import com.heuristiq.uam.organization.subscription.TenantApplication;
import com.heuristiq.uam.organization.subscription.repository.TenantApplicationRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Test double of tenant_applications (organization-application links). */
public final class InMemoryTenantApplicationRepository implements TenantApplicationRepository {
    public final List<TenantApplication> rows = new ArrayList<>();

    public Optional<TenantApplication> find(String tenantId, String code) {
        return findByTenantIdAndApplicationCode(tenantId, code);
    }

    @Override
    public TenantApplication save(TenantApplication subscription) {
        if (subscription.getId() == null) {
            subscription.setId(UUID.randomUUID().toString());
            rows.add(subscription);
        }
        return subscription;
    }

    @Override
    public Optional<TenantApplication> findByTenantIdAndApplicationCode(String tenantId, String applicationCode) {
        return rows.stream().filter(r -> r.getTenantId().equals(tenantId) && r.getApplicationCode().equals(applicationCode)).findFirst();
    }

    @Override
    public List<TenantApplication> findByTenantId(String tenantId) {
        return rows.stream().filter(r -> r.getTenantId().equals(tenantId)).toList();
    }

    @Override
    public List<TenantApplication> findByApplicationCode(String applicationCode) {
        return rows.stream().filter(r -> r.getApplicationCode().equals(applicationCode)).toList();
    }
}
