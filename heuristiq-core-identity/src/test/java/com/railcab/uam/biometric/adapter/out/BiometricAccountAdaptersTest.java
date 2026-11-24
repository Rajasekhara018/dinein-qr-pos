package com.heuristiq.uam.biometric.adapter.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.heuristiq.uam.audit.AuditLog;
import com.heuristiq.uam.audit.AuditLogServiceUseCase;
import com.heuristiq.uam.auth.AuthConstants;
import com.heuristiq.uam.auth.AuthServiceUseCase;
import com.heuristiq.uam.biometric.domain.BiometricConstants;
import com.heuristiq.uam.biometric.domain.BiometricCredential;
import com.heuristiq.uam.biometric.domain.BiometricException;
import com.heuristiq.uam.biometric.domain.BiometricRevocationReason;
import com.heuristiq.uam.dto.LoginResponse;
import com.heuristiq.uam.enums.UamEnums;
import com.heuristiq.uam.lockout.LoginAttemptServiceUseCase;
import com.heuristiq.uam.session.UserSession;
import com.heuristiq.uam.session.repository.UserSessionRepository;
import com.heuristiq.uam.usercredentials.UserCredentialServiceUseCase;
import com.heuristiq.uam.users.UamUser;
import com.heuristiq.uam.users.repository.UamUserRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** The adapters between biometric sign-in and the rest of UAM: re-authentication proofs, sign-in hand-over, audit entries. */
class BiometricAccountAdaptersTest {

    private static final LocalDateTime LOGIN_AT = LocalDateTime.of(2026, 11, 22, 10, 0);

    private final UserSessionRepository sessions = mock(UserSessionRepository.class);
    private final UserCredentialServiceUseCase credentials = mock(UserCredentialServiceUseCase.class);
    private final LoginAttemptServiceUseCase loginAttempts = mock(LoginAttemptServiceUseCase.class);
    private final UamUserRepository users = mock(UamUserRepository.class);
    private final UamAccountReauthentication reauth =
            new UamAccountReauthentication(sessions, credentials, loginAttempts, users, ZoneOffset.UTC);

    private UserSession session(String userId, String authMethod) {
        return UserSession.builder().sessionId("s1").userId(userId).active(true).revoked(false).forcedLogout(false)
                .loginAt(LOGIN_AT).authMethod(authMethod).build();
    }

    @Test
    void theInteractiveSignInTimeComesFromTheCallersOwnLiveSession() {
        when(sessions.findBySessionId("s1")).thenReturn(Optional.of(session("u1", null)));

        assertThat(reauth.interactiveSignInAt("s1", "u1")).contains(LOGIN_AT.toInstant(ZoneOffset.UTC));
        assertThat(reauth.interactiveSignInAt("s1", "u2")).isEmpty();
    }

    @Test
    void aBiometricOrEndedSessionGivesNoSignInTime() {
        when(sessions.findBySessionId("s1")).thenReturn(Optional.of(session("u1", AuthConstants.AUTH_METHOD_BIOMETRIC)));
        assertThat(reauth.interactiveSignInAt("s1", "u1")).isEmpty();

        UserSession revoked = session("u1", null);
        revoked.setRevoked(true);
        when(sessions.findBySessionId("s1")).thenReturn(Optional.of(revoked));
        assertThat(reauth.interactiveSignInAt("s1", "u1")).isEmpty();
    }

    @Test
    void theCurrentPasswordIsCheckedUnderTheSignInLockout() {
        when(users.findById("u1")).thenReturn(Optional.of(UamUser.builder().id("u1").userName("jane").build()));
        when(loginAttempts.isLocked(any())).thenReturn(new LoginAttemptServiceUseCase.IsLockedOutput(false));
        when(credentials.verifyPassword(new UserCredentialServiceUseCase.VerifyPasswordInput("u1", "right")))
                .thenReturn(new UserCredentialServiceUseCase.VerifyPasswordOutput(true));
        when(credentials.verifyPassword(new UserCredentialServiceUseCase.VerifyPasswordInput("u1", "wrong")))
                .thenReturn(new UserCredentialServiceUseCase.VerifyPasswordOutput(false));

        assertThat(reauth.passwordMatches("u1", "right")).isTrue();
        verify(loginAttempts, never()).recordFailure(any());
        assertThat(reauth.passwordMatches("u1", "wrong")).isFalse();
        verify(loginAttempts).recordFailure(new LoginAttemptServiceUseCase.RecordFailureInput("jane"));

        when(loginAttempts.isLocked(any())).thenReturn(new LoginAttemptServiceUseCase.IsLockedOutput(true));
        assertThat(reauth.passwordMatches("u1", "right")).isFalse();
        assertThat(reauth.passwordMatches("missing", "right")).isFalse();
    }

    @Test
    void signInHandsTheAccountToTheSharedLoginAndRefusesAVanishedAccount() {
        AuthServiceUseCase auth = mock(AuthServiceUseCase.class);
        UamUser user = UamUser.builder().id("u1").userName("jane").status(UamEnums.UserStatus.ACTIVE).build();
        LoginResponse response = mock(LoginResponse.class);
        when(users.findById("u1")).thenReturn(Optional.of(user));
        when(auth.loginWithBiometric(new AuthServiceUseCase.LoginWithBiometricInput(user)))
                .thenReturn(new AuthServiceUseCase.LoginWithBiometricOutput(response));
        AuthServiceBiometricSignIn signIn = new AuthServiceBiometricSignIn(users, auth);

        assertThat(signIn.signIn("u1")).isSameAs(response);
        assertThatThrownBy(() -> signIn.signIn("gone")).isInstanceOf(BiometricException.class)
                .extracting(e -> ((BiometricException) e).kind()).isEqualTo(BiometricException.Kind.SIGNIN_INVALID);
    }

    @Test
    void auditEntriesCarryIdsAndReasonsButNoKeyMaterial() {
        AuditLogServiceUseCase auditLogs = mock(AuditLogServiceUseCase.class);
        AuditLogBiometricAudit audit = new AuditLogBiometricAudit(auditLogs);
        BiometricCredential credential = JdbcBiometricStoresTest.credential("c1", "u1", "inst-0001", Instant.parse("2026-11-22T10:00:00Z"));

        audit.enabled(credential);
        audit.signedIn(credential);
        audit.signInFailed(null, "inst-0009", BiometricConstants.FAILURE_RATE_LIMITED);
        audit.revoked(credential, BiometricRevocationReason.PASSWORD_RESET);

        ArgumentCaptor<AuditLogServiceUseCase.RecordInput> entries = ArgumentCaptor.forClass(AuditLogServiceUseCase.RecordInput.class);
        verify(auditLogs, org.mockito.Mockito.times(4)).record(entries.capture());
        assertThat(entries.getAllValues()).extracting(e -> e.entry().getAction()).containsExactly(UamEnums.AuditAction.BIOMETRIC_ENABLED,
                UamEnums.AuditAction.BIOMETRIC_SIGNIN, UamEnums.AuditAction.BIOMETRIC_SIGNIN_FAILED, UamEnums.AuditAction.BIOMETRIC_REVOKED);
        AuditLog enabled = entries.getAllValues().get(0).entry();
        assertThat(enabled.getUserId()).isEqualTo("u1");
        assertThat(enabled.getEntityId()).isEqualTo("c1");
        assertThat(enabled.getNewValue()).containsEntry("installationId", "inst-0001").doesNotContainValue("PUBKEY");
        AuditLog failed = entries.getAllValues().get(2).entry();
        assertThat(failed.getStatus()).isEqualTo(UamEnums.AuditStatus.FAILURE);
        assertThat(failed.getNewValue()).containsEntry("failure", "RATE_LIMITED").containsEntry("installationId", "inst-0009");
        assertThat(entries.getAllValues().get(3).entry().getNewValue()).containsEntry("reason", "PASSWORD_RESET");
    }

    @Test
    void aBrokenAuditWriteNeverFailsTheAction() {
        AuditLogServiceUseCase auditLogs = mock(AuditLogServiceUseCase.class);
        when(auditLogs.record(any())).thenThrow(new IllegalStateException("down"));

        new AuditLogBiometricAudit(auditLogs).signedIn(JdbcBiometricStoresTest.credential("c1", "u1", "inst-0001", Instant.now()));
    }
}
