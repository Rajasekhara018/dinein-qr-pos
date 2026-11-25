package com.heuristiq.identity.otp.repository.jpa;

import com.heuristiq.identity.otp.OtpChallenge;
import com.heuristiq.identity.otp.OtpStore;
import com.heuristiq.identity.otp.persistence.jpa.OtpChallengeEntity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

/** PostgreSQL adapter for {@link OtpStore}; the JPA {@code @Version} column provides the compare-and-save. */
@Component
@ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "postgres")
class JpaOtpStore implements OtpStore {
    private final OtpChallengeJpaSpringRepository repository;

    JpaOtpStore(OtpChallengeJpaSpringRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<OtpChallenge> find(String key) {
        return repository.findById(key).map(OtpChallengeEntity::toChallenge);
    }

    @Override
    public boolean compareAndSave(OtpChallenge challenge) {
        if (challenge.getVersion() == null) {
            // No TTL index in Postgres -- clear out retained rows nobody needs before inserting a new one.
            repository.deleteExpired(Instant.now());
        }
        try {
            OtpChallengeEntity saved = repository.saveAndFlush(OtpChallengeEntity.from(challenge));
            challenge.setVersion(saved.getVersion());
            return true;
        } catch (OptimisticLockingFailureException | DataIntegrityViolationException lostRace) {
            return false;
        }
    }
}
