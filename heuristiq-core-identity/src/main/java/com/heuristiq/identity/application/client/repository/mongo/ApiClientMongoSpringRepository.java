package com.heuristiq.uam.application.client.repository.mongo;

import com.heuristiq.uam.application.client.persistence.mongo.ApiClientDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

interface ApiClientMongoSpringRepository extends MongoRepository<ApiClientDocument, String> {
    Optional<ApiClientDocument> findByClientIdIgnoreCase(String clientId);

    boolean existsByClientIdIgnoreCase(String clientId);
}
