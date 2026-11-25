package com.heuristiq.identity.lockout;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface LoginAttemptRepository extends MongoRepository<LoginAttemptDocument, String> {
}
