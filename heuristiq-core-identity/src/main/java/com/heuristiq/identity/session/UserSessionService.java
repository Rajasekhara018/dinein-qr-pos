package com.heuristiq.identity.session;

import com.heuristiq.identity.common.UamException;
import com.heuristiq.identity.enums.UamEnums.LogoutReason;
import com.heuristiq.identity.session.repository.UserSessionRepository;
import com.heuristiq.identity.biometric.application.BiometricRevocationUseCase;
import com.heuristiq.identity.biometric.application.BiometricRevocations;
import com.heuristiq.identity.biometric.domain.BiometricRevocationReason;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class UserSessionService implements UserSessionServiceUseCase {
    private final UserSessionRepository sessionRepository;
    /** "Log out of all devices" also ends the account's fingerprint / Face ID devices (resolved lazily: no bean cycle). */
    private final ObjectProvider<BiometricRevocationUseCase> biometricRevocation;

    /** Without biometric revocation (tests that do not need it). */
    public UserSessionService(UserSessionRepository sessionRepository) {
        this(sessionRepository, null);
    }

    @Autowired
    public UserSessionService(UserSessionRepository sessionRepository, ObjectProvider<BiometricRevocationUseCase> biometricRevocation) {
        this.sessionRepository = sessionRepository;
        this.biometricRevocation = biometricRevocation;
    }

    public ListByUserOutput listByUser(ListByUserInput input) {
        return new ListByUserOutput(sessionRepository.findByUserIdAndActiveTrue(input.userId()));
    }

    public GetOutput get(GetInput input) {
        UserSession session = sessionRepository.findBySessionId(input.sessionId())
                .orElseThrow(() -> UamException.notFound("Session not found"));
        return new GetOutput(session);
    }

    public RevokeOutput revoke(RevokeInput input) {
        UserSession session = get(new GetInput(input.sessionId())).session();
        deactivate(session, LogoutReason.ADMIN_LOGOUT);
        sessionRepository.save(session);
        return new RevokeOutput();
    }

    /**
     * "Log out of all devices" (the person's own, or an administrator's for them): every active session ends, and so does
     * every fingerprint / Face ID device, which could otherwise sign straight back in.
     */
    public RevokeAllForUserOutput revokeAllForUser(RevokeAllForUserInput input) {
        BiometricRevocations.revokeAll(biometricRevocation, input.userId(), BiometricRevocationReason.LOGOUT_ALL);
        List<UserSession> sessions = listByUser(new ListByUserInput(input.userId())).sessions();
        sessions.forEach(session -> deactivate(session, LogoutReason.ADMIN_LOGOUT));
        sessionRepository.saveAll(sessions);
        return new RevokeAllForUserOutput();
    }

    private void deactivate(UserSession session, LogoutReason reason) {
        LocalDateTime now = LocalDateTime.now();
        session.setActive(false);
        session.setRevoked(true);
        session.setForcedLogout(true);
        session.setLogoutAt(now);
        session.setLogoutReason(reason);
        session.setUpdatedAt(now);
    }
}
