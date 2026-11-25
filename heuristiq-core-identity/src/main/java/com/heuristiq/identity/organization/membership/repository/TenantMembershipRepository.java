package com.heuristiq.identity.organization.membership.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.heuristiq.identity.organization.membership.TenantMembership;
import com.heuristiq.identity.organization.membership.TenantMembershipStatus;

/** Port for organization memberships; one adapter per persistence backend (Postgres / Mongo). */
public interface TenantMembershipRepository {

    TenantMembership save(TenantMembership membership);

    Optional<TenantMembership> findByTenantIdAndUserId(String tenantId, String userId);

    /** Every membership (removed ones too) of one account. Bounded by the number of organizations a person works in. */
    List<TenantMembership> findByUserId(String userId);

    Page<TenantMembership> findByTenantId(String tenantId, Pageable pageable);

    long countByTenantIdAndStatus(String tenantId, TenantMembershipStatus status);
}
