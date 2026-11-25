package com.heuristiq.identity.organization.subscription.repository;

import java.util.List;
import java.util.Optional;

import com.heuristiq.identity.organization.subscription.TenantApplication;

/** Port for organization-application subscriptions; one adapter per persistence backend (Postgres / Mongo). */
public interface TenantApplicationRepository {

    TenantApplication save(TenantApplication subscription);

    Optional<TenantApplication> findByTenantIdAndApplicationCode(String tenantId, String applicationCode);

    /** Every subscription (cancelled ones too) of one organization. Bounded by the number of applications on the platform. */
    List<TenantApplication> findByTenantId(String tenantId);

    /** Every link (cancelled ones too) of one application, across organizations. Bounded by the number of organizations. */
    List<TenantApplication> findByApplicationCode(String applicationCode);
}
