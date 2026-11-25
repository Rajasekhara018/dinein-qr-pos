package com.heuristiq.identity.otp;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface OtpChallengeRepository extends MongoRepository<OtpChallengeDocument, String> {
}
