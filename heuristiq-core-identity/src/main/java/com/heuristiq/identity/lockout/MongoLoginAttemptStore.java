package com.heuristiq.identity.lockout;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "mongo", matchIfMissing = true)
class MongoLoginAttemptStore implements LoginAttemptStore {
    private final LoginAttemptRepository repository;

    MongoLoginAttemptStore(LoginAttemptRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<LoginAttemptState> find(String username) {
        return repository.findById(username)
                .map(doc -> new LoginAttemptState(doc.getUsername(), doc.getFailures(), doc.getLockedUntil(), doc.getExpiresAt()));
    }

    @Override
    public void save(LoginAttemptState state) {
        repository.save(new LoginAttemptDocument(state.username(), state.failures(), state.lockedUntil(), state.expiresAt()));
    }

    @Override
    public void delete(String username) {
        repository.deleteById(username);
    }
}
