package com.heuristiq.uam.organization.repository.mongo;

import com.heuristiq.uam.organization.persistence.mongo.TenantDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

interface TenantMongoSpringRepository extends MongoRepository<TenantDocument, String> {
    Optional<TenantDocument> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);
}
