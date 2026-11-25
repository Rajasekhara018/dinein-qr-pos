package com.heuristiq.identity.session.repository;

import com.heuristiq.identity.session.UserSession;

import java.util.List;
import java.util.Optional;

public interface UserSessionRepository {
    UserSession save(UserSession session);

    List<UserSession> saveAll(List<UserSession> sessions);

    List<UserSession> findByUserIdAndActiveTrue(String userId);

    Optional<UserSession> findBySessionId(String sessionId);
}
