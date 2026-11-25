package com.heuristiq.identity.lockout.repository.jpa;

import com.heuristiq.identity.lockout.persistence.jpa.LoginAttemptEntity;
import org.springframework.data.jpa.repository.JpaRepository;

interface LoginAttemptJpaSpringRepository extends JpaRepository<LoginAttemptEntity, String> {
}
