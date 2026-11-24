package com.heuristiq.uam.passwordsHistory.repository.mongo;

import com.heuristiq.uam.passwordsHistory.persistance.mongo.PasswordHistoryDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

interface PasswordHistoryMongoSpringRepository extends MongoRepository<PasswordHistoryDocument, String> {
    Page<PasswordHistoryDocument> findByUserId(String userId, Pageable pageable);
}
