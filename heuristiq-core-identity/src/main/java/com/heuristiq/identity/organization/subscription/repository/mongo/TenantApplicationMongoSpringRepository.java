package com.heuristiq.uam.organization.subscription.repository.mongo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.heuristiq.uam.organization.subscription.persistence.mongo.TenantApplicationDocument;

interface TenantApplicationMongoSpringRepository extends MongoRepository<TenantApplicationDocument, String> {
    Optional<TenantApplicationDocument> findByTenantIdAndApplicationCode(String tenantId, String applicationCode);

    List<TenantApplicationDocument> findByTenantId(String tenantId);

    List<TenantApplicationDocument> findByApplicationCode(String applicationCode);
}
