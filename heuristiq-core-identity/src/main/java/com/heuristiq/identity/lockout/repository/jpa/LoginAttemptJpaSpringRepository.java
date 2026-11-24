package com.heuristiq.uam.lockout.repository.jpa;

import com.heuristiq.uam.lockout.persistence.jpa.LoginAttemptEntity;
import org.springframework.data.jpa.repository.JpaRepository;

interface LoginAttemptJpaSpringRepository extends JpaRepository<LoginAttemptEntity, String> {
}
