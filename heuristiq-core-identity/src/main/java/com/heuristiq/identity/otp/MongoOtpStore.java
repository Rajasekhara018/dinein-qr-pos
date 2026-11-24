package com.heuristiq.uam.otp;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** MongoDB adapter for {@link OtpStore}; the {@code @Version} field provides the compare-and-save. */
@Component
@ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "mongo", matchIfMissing = true)
public class MongoOtpStore implements OtpStore {
    private final OtpChallengeRepository repository;

    public MongoOtpStore(OtpChallengeRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<OtpChallenge> find(String key) {
        return repository.findById(key).map(OtpChallengeDocument::toChallenge);
    }

    @Override
    public boolean compareAndSave(OtpChallenge challenge) {
        try {
            OtpChallengeDocument saved = repository.save(OtpChallengeDocument.from(challenge));
            challenge.setVersion(saved.getVersion());
            return true;
        } catch (OptimisticLockingFailureException | DuplicateKeyException lostRace) {
            return false;
        }
    }
}
