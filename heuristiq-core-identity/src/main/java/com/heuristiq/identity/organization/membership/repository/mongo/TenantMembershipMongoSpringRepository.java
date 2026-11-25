package com.heuristiq.identity.organization.membership.repository.mongo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import com.heuristiq.identity.organization.membership.TenantMembershipStatus;
import com.heuristiq.identity.organization.membership.persistence.mongo.TenantMembershipDocument;

interface TenantMembershipMongoSpringRepository extends MongoRepository<TenantMembershipDocument, String> {
    Optional<TenantMembershipDocument> findByTenantIdAndUserId(String tenantId, String userId);

    List<TenantMembershipDocument> findByUserId(String userId);

    Page<TenantMembershipDocument> findByTenantId(String tenantId, Pageable pageable);

    long countByTenantIdAndStatus(String tenantId, TenantMembershipStatus status);
}
