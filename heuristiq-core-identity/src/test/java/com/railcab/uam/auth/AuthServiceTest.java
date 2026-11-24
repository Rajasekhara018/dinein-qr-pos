package com.heuristiq.uam.auth;

import com.heuristiq.uam.common.UamCommonConstants;
import com.heuristiq.resilient.DownstreamUnavailableException;
import com.heuristiq.uam.audit.AuditLog;
import com.heuristiq.uam.audit.AuditLogServiceUseCase;
import com.heuristiq.uam.audit.authevent.AuthAuditService;
import com.heuristiq.uam.auth.AuthServiceUseCase.LoginInput;
import com.heuristiq.uam.auth.AuthServiceUseCase.LoginViaExternalProviderInput;
import com.heuristiq.uam.auth.AuthServiceUseCase.RefreshInput;
import com.heuristiq.uam.common.UamException;
import com.heuristiq.uam.dto.LoginRequest;
import com.heuristiq.uam.dto.LoginResponse;
import com.heuristiq.uam.dto.MfaChallengeRequiredResponse;
import com.heuristiq.uam.enums.UamEnums;
import com.heuristiq.uam.lockout.LoginAttemptServiceUseCase;
import com.heuristiq.uam.lockout.UserLoginSecurityServiceUseCase;
import com.heuristiq.uam.metrics.UamMetrics;
import com.heuristiq.uam.mfa.UserMfa;
import com.heuristiq.uam.mfa.UserMfaServiceUseCase;
import com.heuristiq.uam.refreshToken.RefreshToken;
import com.heuristiq.uam.usercredentials.UserCredentialServiceUseCase;
import com.heuristiq.uam.users.UamUser;
import com.heuristiq.uam.users.repository.UamUserRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {
    private UamUserRepository users;
    private UserCredentialServiceUseCase credentials;
    private LoginAttemptServiceUseCase attempts;
    private AuthAuditService authAudit;
    private UserMfaServiceUseCase mfaService;
    private UserLoginSecurityServiceUseCase loginSecurity;
    private AuditLogServiceUseCase auditLog;
    private LoginSessionService sessions;
    private AuthService service;
    private UamUser user;
    private LoginResponse session;

    @BeforeEach
    void setUp() {
        users = mock(UamUserRepository.class);
        credentials = mock(UserCredentialServiceUseCase.class);
        attempts = mock(LoginAttemptServiceUseCase.class);
        authAudit = mock(AuthAuditService.class);
        mfaService = mock(UserMfaServiceUseCase.class);
        loginSecurity = mock(UserLoginSecurityServiceUseCase.class);
        auditLog = mock(AuditLogServiceUseCase.class);
        sessions = mock(LoginSessionService.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("203.0.113.9");
        when(request.getHeader("User-Agent")).thenReturn("JUnit");
        service = new AuthService(users, credentials, attempts, authAudit, mfaService, loginSecurity, auditLog,
                sessions, request, new UamMetrics(new SimpleMeterRegistry()));

        user = UamUser.builder().id("u1").userName("jane").tenantId("t1").status(UamEnums.UserStatus.ACTIVE).build();
        session = mock(LoginResponse.class);
        when(users.findByUserNameIgnoreCase("jane")).thenReturn(Optional.of(user));
        when(users.findByUserNameIgnoreCase("jane@x.com")).thenReturn(Optional.empty());
        when(users.findByEmailIgnoreCase("jane@x.com")).thenReturn(Optional.of(user));
        when(attempts.isLocked(any())).thenReturn(new LoginAttemptServiceUseCase.IsLockedOutput(false));
        when(credentials.verifyPassword(any())).thenReturn(new UserCredentialServiceUseCase.VerifyPasswordOutput(true));
        when(mfaService.listEnabledByUser(any())).thenReturn(new UserMfaServiceUseCase.ListEnabledByUserOutput(List.of()));
        when(sessions.issue(any(), any(), any(), anyBoolean())).thenReturn(session);
    }

    private LoginInput login(String username, String password, String totp) {
        return new LoginInput(new LoginRequest(username, password, totp));
    }

    private UserMfa totp(boolean primary) {
        return UserMfa.builder().id("m").userId("u1").mfaType(UamEnums.MfaType.TOTP).primary(primary).enabled(true).build();
    }

    @Test
    void successfulPasswordLoginIssuesSessionAndAudits() {
        Object result = service.login(login("jane", "pw", null)).result();

        assertThat(result).isSameAs(session);
        verify(sessions).issue(user, "203.0.113.9", "JUnit", false);
        verify(attempts).recordSuccess(new LoginAttemptServiceUseCase.RecordSuccessInput("jane"));
        verify(loginSecurity).recordSuccess(new UserLoginSecurityServiceUseCase.RecordSuccessInput("u1", "203.0.113.9"));
        verify(authAudit).loginSucceeded(user);
        verify(auditLog).record(any(AuditLogServiceUseCase.RecordInput.class));
    }

    @Test
    void anAccountWithNoRolesApplicationsOrOrganizationStillSignsIn() {
        UamUser bare = UamUser.builder().id("u2").userName("bare").status(UamEnums.UserStatus.ACTIVE).build();
        when(users.findByUserNameIgnoreCase("bare")).thenReturn(Optional.of(bare));

        assertThat(service.login(login("bare", "pw", null)).result()).isSameAs(session);
        verify(sessions).issue(bare, "203.0.113.9", "JUnit", false);
    }

    @Test
    void anAccountStillWaitingForItsFirstPasswordIsRefusedWithTheNotActiveMessageNotAnUnexpectedError() {
        UamUser pending = UamUser.builder().id("u3").userName("agent").tenantId("t1").status(UamEnums.UserStatus.PENDING).build();
        when(users.findByUserNameIgnoreCase("agent")).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.login(login("agent", "pw", null)))
                .isInstanceOf(UamException.class).hasMessage(UamCommonConstants.USER_NOT_ACTIVE_MESSAGE);
        verify(sessions, never()).issue(any(), any(), any(), anyBoolean());
    }

    @Test
    void whenTheFranchiseServiceCannotAnswerTheSignInFailsAsUnavailableAndIsNotCountedAsAWrongPassword() {
        DownstreamUnavailableException unavailable = new DownstreamUnavailableException(
                "franchise-service", DownstreamUnavailableException.Reason.TIMEOUT, new RuntimeException("slow"));
        when(sessions.issue(any(), any(), any(), anyBoolean())).thenThrow(unavailable);

        assertThatThrownBy(() -> service.login(login("jane", "pw", null))).isSameAs(unavailable);
        verify(attempts, never()).recordFailure(any());
        verify(authAudit, never()).loginSucceeded(any());
    }

    @Test
    void loginResolvesEmailIdentifier() {
        assertThat(service.login(login("jane@x.com", "pw", null)).result()).isSameAs(session);
    }

    @Test
    void loginResolvesPhoneIdentifier() {
        when(users.findByUserNameIgnoreCase("9876543210")).thenReturn(Optional.empty());
        when(users.findByEmailIgnoreCase("9876543210")).thenReturn(Optional.empty());
        when(users.findByPhone("9876543210")).thenReturn(Optional.of(user));
        assertThat(service.login(login("9876543210", "pw", null)).result()).isSameAs(session);
    }

    @Test
    void lockedAccountIsBlockedBeforeAnyLookup() {
        when(attempts.isLocked(any())).thenReturn(new LoginAttemptServiceUseCase.IsLockedOutput(true));

        assertThatThrownBy(() -> service.login(login("jane", "pw", null)))
                .isInstanceOf(UamException.class).hasMessage(AuthConstants.LOGIN_LOCKED_MESSAGE);

        verify(authAudit).loginBlocked("jane");
        verify(users, never()).findByUserNameIgnoreCase(any());
    }

    @Test
    void unknownUserIsRejectedWithGenericMessageAndCounted() {
        when(users.findByUserNameIgnoreCase("ghost")).thenReturn(Optional.empty());
        when(users.findByEmailIgnoreCase("ghost")).thenReturn(Optional.empty());
        when(users.findByPhone("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(login("ghost", "pw", null)))
                .hasMessage(AuthConstants.INVALID_CREDENTIALS_MESSAGE);

        verify(attempts).recordFailure(new LoginAttemptServiceUseCase.RecordFailureInput("ghost"));
        verify(authAudit).loginFailed("ghost", AuthConstants.INVALID_CREDENTIALS_MESSAGE);
        verify(loginSecurity, never()).recordFailure(any());
    }

    @Test
    void inactiveAccountIsRejectedAndRecorded() {
        user.setStatus(UamEnums.UserStatus.SUSPENDED);

        assertThatThrownBy(() -> service.login(login("jane", "pw", null))).isInstanceOf(UamException.class);

        verify(loginSecurity).recordFailure(new UserLoginSecurityServiceUseCase.RecordFailureInput("u1", "203.0.113.9", false));
        verify(credentials, never()).verifyPassword(any());
    }

    @Test
    void wrongPasswordRecordsFailureAndLockState() {
        when(credentials.verifyPassword(any())).thenReturn(new UserCredentialServiceUseCase.VerifyPasswordOutput(false));
        when(attempts.isLocked(any())).thenReturn(new LoginAttemptServiceUseCase.IsLockedOutput(false),
                new LoginAttemptServiceUseCase.IsLockedOutput(false), new LoginAttemptServiceUseCase.IsLockedOutput(true));

        assertThatThrownBy(() -> service.login(login("jane", "bad", null)))
                .hasMessage(AuthConstants.INVALID_CREDENTIALS_MESSAGE);

        verify(attempts).recordFailure(new LoginAttemptServiceUseCase.RecordFailureInput("jane"));
        verify(sessions, never()).issue(any(), any(), any(), anyBoolean());
    }

    @Test
    void mfaEnrolledUserWithoutCodeGetsChallenge() {
        when(mfaService.listEnabledByUser(any())).thenReturn(new UserMfaServiceUseCase.ListEnabledByUserOutput(
                List.of(totp(false), totp(true))));

        Object result = service.login(login("jane", "pw", null)).result();

        assertThat(result).isInstanceOf(MfaChallengeRequiredResponse.class);
        verify(sessions, never()).issue(any(), any(), any(), anyBoolean());
        verify(attempts, never()).recordSuccess(any());
    }

    @Test
    void mfaCodeIsVerifiedAndSessionMarkedMfaVerified() {
        UserMfa mfa = totp(true);
        when(mfaService.listEnabledByUser(any())).thenReturn(new UserMfaServiceUseCase.ListEnabledByUserOutput(List.of(mfa)));
        when(mfaService.verifyCode(new UserMfaServiceUseCase.VerifyCodeInput(mfa, "123456")))
                .thenReturn(new UserMfaServiceUseCase.VerifyCodeOutput(true));

        assertThat(service.login(login("jane", "pw", "123456")).result()).isSameAs(session);

        verify(sessions).issue(user, "203.0.113.9", "JUnit", true);
    }

    @Test
    void wrongMfaCodeIsRejected() {
        UserMfa mfa = totp(true);
        when(mfaService.listEnabledByUser(any())).thenReturn(new UserMfaServiceUseCase.ListEnabledByUserOutput(List.of(mfa)));
        when(mfaService.verifyCode(any())).thenReturn(new UserMfaServiceUseCase.VerifyCodeOutput(false));

        assertThatThrownBy(() -> service.login(login("jane", "pw", "000000")))
                .hasMessage(AuthConstants.INVALID_AUTHENTICATOR_CODE_MESSAGE);
        verify(sessions, never()).issue(any(), any(), any(), anyBoolean());
    }

    @Test
    void brokenAuditWriteNeverFailsLogin() {
        doThrow(new IllegalStateException("db down")).when(auditLog).record(any());
        assertThat(service.login(login("jane", "pw", null)).result()).isSameAs(session);
    }

    @Test
    void auditEntryCarriesTenantAndUser() {
        service.login(login("jane", "pw", null));
        org.mockito.ArgumentCaptor<AuditLogServiceUseCase.RecordInput> captor =
                org.mockito.ArgumentCaptor.forClass(AuditLogServiceUseCase.RecordInput.class);
        verify(auditLog).record(captor.capture());
        AuditLog entry = captor.getValue().entry();
        assertThat(entry.getTenantId()).isEqualTo("t1");
        assertThat(entry.getUserId()).isEqualTo("u1");
        assertThat(entry.getStatus()).isEqualTo(UamEnums.AuditStatus.SUCCESS);
        assertThat(entry.getIpAddress()).isEqualTo("203.0.113.9");
    }

    @Test
    void externalProviderLoginDoesNotAssumeMfaWasVerified() {
        assertThat(service.loginViaExternalProvider(new LoginViaExternalProviderInput(user)).response()).isSameAs(session);
        verify(sessions).issue(eq(user), any(), any(), eq(false));
    }

    @Test
    void refreshRequiresTokenKnownTokenAndActiveUser() {
        assertThatThrownBy(() -> service.refresh(new RefreshInput(" ")))
                .hasMessageContaining("Refresh token is required");

        when(sessions.resolveForRefresh("bad")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.refresh(new RefreshInput("bad")))
                .hasMessageContaining("Invalid or expired")
                .isInstanceOfSatisfying(UamException.class, e -> {
                    // 401 + stable code: clients sign the user out on this, and only on this
                    assertThat(e.getStatus()).isEqualTo(org.springframework.http.HttpStatus.UNAUTHORIZED);
                    assertThat(e.getErrorCode()).isEqualTo(com.heuristiq.uam.refreshToken.RefreshTokenConstants.ERROR_REFRESH_TOKEN_INVALID);
                });

        user.setStatus(UamEnums.UserStatus.DISABLED);
        when(sessions.resolveForRefresh("tok")).thenReturn(
                Optional.of(new LoginSessionService.ResolvedRefresh(user, new RefreshToken())));
        assertThatThrownBy(() -> service.refresh(new RefreshInput("tok"))).isInstanceOf(UamException.class);
        verify(sessions, never()).rotate(any());
    }

    @Test
    void refreshRotatesTokenForActiveUser() {
        LoginSessionService.ResolvedRefresh resolved = new LoginSessionService.ResolvedRefresh(user, new RefreshToken());
        when(sessions.resolveForRefresh("tok")).thenReturn(Optional.of(resolved));
        when(sessions.rotate(resolved)).thenReturn(session);

        assertThat(service.refresh(new RefreshInput("tok")).response()).isSameAs(session);
    }
}
