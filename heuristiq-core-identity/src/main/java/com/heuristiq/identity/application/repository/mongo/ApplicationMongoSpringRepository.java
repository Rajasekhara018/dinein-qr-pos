package com.heuristiq.identity.application.repository.mongo;

import com.heuristiq.identity.application.persistence.mongo.ApplicationDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

interface ApplicationMongoSpringRepository extends MongoRepository<ApplicationDocument, String> {
    Optional<ApplicationDocument> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);
}
