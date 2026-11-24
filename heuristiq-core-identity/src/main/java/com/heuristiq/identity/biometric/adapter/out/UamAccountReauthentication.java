package com.heuristiq.uam.biometric.adapter.out;

import com.heuristiq.uam.auth.AuthConstants;
import com.heuristiq.uam.biometric.application.port.AccountReauthentication;
import com.heuristiq.uam.lockout.LoginAttemptServiceUseCase;
import com.heuristiq.uam.session.UserSession;
import com.heuristiq.uam.session.repository.UserSessionRepository;
import com.heuristiq.uam.usercredentials.UserCredentialServiceUseCase;
import com.heuristiq.uam.users.UamUser;
import com.heuristiq.uam.users.repository.UamUserRepository;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

/**
 * Re-authentication proofs from UAM's own records: the sign-in time of the caller's session ({@code loginAt}, set once at
 * sign-in and never moved by a refresh; a biometric session does not count), and the account's current password checked
 * with UAM's password encoder under the shared sign-in lockout.
 */
public class UamAccountReauthentication implements AccountReauthentication {

    private final UserSessionRepository sessions;
    private final UserCredentialServiceUseCase credentials;
    private final LoginAttemptServiceUseCase loginAttempts;
    private final UamUserRepository users;
    private final ZoneId storedZone;

    /**
     * @param storedZone zone of the session's stored local times (the JVM zone UAM writes them in, UTC in deployments)
     */
    public UamAccountReauthentication(UserSessionRepository sessions, UserCredentialServiceUseCase credentials,
                                      LoginAttemptServiceUseCase loginAttempts, UamUserRepository users, ZoneId storedZone) {
        this.sessions = sessions;
        this.credentials = credentials;
        this.loginAttempts = loginAttempts;
        this.users = users;
        this.storedZone = storedZone;
    }

    @Override
    public Optional<Instant> interactiveSignInAt(String sessionId, String userId) {
        return sessions.findBySessionId(sessionId)
                .filter(session -> userId.equals(session.getUserId()))
                .filter(UamAccountReauthentication::live)
                .filter(session -> !AuthConstants.AUTH_METHOD_BIOMETRIC.equals(session.getAuthMethod()))
                .map(UserSession::getLoginAt)
                .map(loginAt -> loginAt.atZone(storedZone).toInstant());
    }

    @Override
    public boolean passwordMatches(String userId, String password) {
        Optional<UamUser> user = users.findById(userId);
        if (user.isEmpty()) {
            return false;
        }
        String username = user.get().getUserName();
        if (loginAttempts.isLocked(new LoginAttemptServiceUseCase.IsLockedInput(username)).locked()) {
            return false;
        }
        boolean verified = credentials.verifyPassword(new UserCredentialServiceUseCase.VerifyPasswordInput(userId, password)).verified();
        if (!verified) {
            loginAttempts.recordFailure(new LoginAttemptServiceUseCase.RecordFailureInput(username));
        }
        return verified;
    }

    private static boolean live(UserSession session) {
        return Boolean.TRUE.equals(session.getActive()) && !Boolean.TRUE.equals(session.getRevoked())
                && !Boolean.TRUE.equals(session.getForcedLogout()) && session.getLoginAt() != null;
    }
}
