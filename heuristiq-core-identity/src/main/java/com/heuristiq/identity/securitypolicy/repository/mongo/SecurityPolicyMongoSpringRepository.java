package com.heuristiq.uam.securitypolicy.repository.mongo;

import com.heuristiq.uam.securitypolicy.persistence.mongo.SecurityPolicyDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

interface SecurityPolicyMongoSpringRepository extends MongoRepository<SecurityPolicyDocument, String> {
    Optional<SecurityPolicyDocument> findByTenantIdAndApplicationId(String tenantId, String applicationId);
}
