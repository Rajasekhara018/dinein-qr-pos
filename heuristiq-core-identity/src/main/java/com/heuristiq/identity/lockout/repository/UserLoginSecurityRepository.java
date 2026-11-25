package com.heuristiq.identity.lockout.repository;

import com.heuristiq.identity.lockout.UserLoginSecurity;

import java.util.Optional;

public interface UserLoginSecurityRepository {
    UserLoginSecurity save(UserLoginSecurity loginSecurity);

    Optional<UserLoginSecurity> findByUserId(String userId);
}
