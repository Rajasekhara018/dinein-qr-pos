package com.heuristiq.identity.session.repository.jpa;

import com.heuristiq.identity.session.persistence.jpa.UserSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface UserSessionJpaSpringRepository extends JpaRepository<UserSessionEntity, String> {
    List<UserSessionEntity> findByUserIdAndActiveTrue(String userId);

    Optional<UserSessionEntity> findBySessionId(String sessionId);
}
