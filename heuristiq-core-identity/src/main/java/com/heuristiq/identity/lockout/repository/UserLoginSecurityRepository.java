package com.heuristiq.uam.lockout.repository;

import com.heuristiq.uam.lockout.UserLoginSecurity;

import java.util.Optional;

public interface UserLoginSecurityRepository {
    UserLoginSecurity save(UserLoginSecurity loginSecurity);

    Optional<UserLoginSecurity> findByUserId(String userId);
}
