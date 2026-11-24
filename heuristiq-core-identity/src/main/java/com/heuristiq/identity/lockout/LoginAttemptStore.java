package com.heuristiq.uam.lockout;

import java.util.Optional;

public interface LoginAttemptStore {
    Optional<LoginAttemptState> find(String username);

    void save(LoginAttemptState state);

    void delete(String username);
}
