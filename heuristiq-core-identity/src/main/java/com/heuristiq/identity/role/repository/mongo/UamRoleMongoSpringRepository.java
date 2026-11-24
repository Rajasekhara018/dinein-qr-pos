package com.heuristiq.uam.role.repository.mongo;

import com.heuristiq.uam.role.persistance.mongo.UamRoleDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

interface UamRoleMongoSpringRepository extends MongoRepository<UamRoleDocument, String> {
    Optional<UamRoleDocument> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);
}
