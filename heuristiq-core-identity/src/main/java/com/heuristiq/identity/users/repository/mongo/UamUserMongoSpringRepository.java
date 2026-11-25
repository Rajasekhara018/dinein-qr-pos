package com.heuristiq.identity.users.repository.mongo;

import com.heuristiq.identity.users.persistence.mongo.UamUserDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

interface UamUserMongoSpringRepository extends MongoRepository<UamUserDocument, String> {
    Optional<UamUserDocument> findByUserNameIgnoreCase(String userName);

    Optional<UamUserDocument> findByEmailIgnoreCase(String email);

    Optional<UamUserDocument> findByPhoneE164(String phoneE164);

    List<UamUserDocument> findAllByPhoneNumber_Number(String number);

    boolean existsByUserNameIgnoreCase(String userName);
}
