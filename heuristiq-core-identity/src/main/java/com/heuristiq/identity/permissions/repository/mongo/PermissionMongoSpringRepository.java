package com.heuristiq.uam.permissions.repository.mongo;

import com.heuristiq.uam.permissions.persistance.mongo.PermissionDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

interface PermissionMongoSpringRepository extends MongoRepository<PermissionDocument, String> {
    Optional<PermissionDocument> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    List<PermissionDocument> findByCodeIn(Collection<String> codes);
}
