package com.heuristiq.identity.lockout.repository.jpa;

import com.heuristiq.identity.lockout.persistence.jpa.UserLoginSecurityEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface UserLoginSecurityJpaSpringRepository extends JpaRepository<UserLoginSecurityEntity, String> {
    Optional<UserLoginSecurityEntity> findByUserId(String userId);
}
