package com.heuristiq.identity.lockout.repository.jpa;

import com.heuristiq.identity.lockout.LoginAttemptState;
import com.heuristiq.identity.lockout.LoginAttemptStore;
import com.heuristiq.identity.lockout.persistence.jpa.LoginAttemptEntity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "postgres")
class JpaLoginAttemptStore implements LoginAttemptStore {
    private final LoginAttemptJpaSpringRepository repository;

    JpaLoginAttemptStore(LoginAttemptJpaSpringRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<LoginAttemptState> find(String username) {
        return repository.findById(username)
                .map(e -> new LoginAttemptState(e.getUsername(), e.getFailures(), e.getLockedUntil(), e.getExpiresAt()));
    }

    @Override
    public void save(LoginAttemptState state) {
        repository.save(new LoginAttemptEntity(state.username(), state.failures(), state.lockedUntil(), state.expiresAt()));
    }

    @Override
    public void delete(String username) {
        repository.deleteById(username);
    }
}
