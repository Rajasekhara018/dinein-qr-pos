package com.heuristiq.uam.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.heuristiq.uam.audit.AuditLogServiceUseCase;
import com.heuristiq.uam.audit.authevent.AuthAuditService;
import com.heuristiq.uam.common.UamException;
import com.heuristiq.uam.dto.LoginResponse;
import com.heuristiq.uam.enums.UamEnums;
import com.heuristiq.uam.lockout.LoginAttemptServiceUseCase;
import com.heuristiq.uam.lockout.UserLoginSecurityServiceUseCase;
import com.heuristiq.uam.metrics.UamMetrics;
import com.heuristiq.uam.mfa.UserMfaServiceUseCase;
import com.heuristiq.uam.usercredentials.UserCredentialServiceUseCase;
import com.heuristiq.uam.users.UamUser;
import com.heuristiq.uam.users.repository.UamUserRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** A proven device key signs in through the same machinery as a password, as a biometric session with MFA satisfied. */
class AuthServiceBiometricLoginTest {

    private LoginAttemptServiceUseCase attempts;
    private LoginSessionService sessions;
    private UserMfaServiceUseCase mfa;
    private AuthService service;
    private LoginResponse response;

    @BeforeEach
    void setUp() {
        attempts = mock(LoginAttemptServiceUseCase.class);
        sessions = mock(LoginSessionService.class);
        mfa = mock(UserMfaServiceUseCase.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("203.0.113.9");
        when(request.getHeader("User-Agent")).thenReturn("JUnit");
        service = new AuthService(mock(UamUserRepository.class), mock(UserCredentialServiceUseCase.class), attempts,
                mock(AuthAuditService.class), mfa, mock(UserLoginSecurityServiceUseCase.class), mock(AuditLogServiceUseCase.class),
                sessions, request, new UamMetrics(new SimpleMeterRegistry()));
        response = mock(LoginResponse.class);
        when(attempts.isLocked(any())).thenReturn(new LoginAttemptServiceUseCase.IsLockedOutput(false));
        when(sessions.issue(any(), any(), any(), anyBoolean(), any())).thenReturn(response);
    }

    private static UamUser user(UamEnums.UserStatus status) {
        return UamUser.builder().id("u1").userName("jane").status(status).build();
    }

    @Test
    void anActiveAccountGetsABiometricSessionWithTheSecondFactorSatisfied() {
        UamUser user = user(UamEnums.UserStatus.ACTIVE);

        assertThat(service.loginWithBiometric(new AuthServiceUseCase.LoginWithBiometricInput(user)).response()).isSameAs(response);

        verify(sessions).issue(user, "203.0.113.9", "JUnit", true, AuthConstants.AUTH_METHOD_BIOMETRIC);
        verify(attempts).recordSuccess(new LoginAttemptServiceUseCase.RecordSuccessInput("jane"));
        // No MFA challenge is ever asked for: the device key is the second factor.
        verify(mfa, never()).listEnabledByUser(any());
    }

    @Test
    void anInactiveOrLockedOutAccountIsRefusedLikeAPasswordLogin() {
        assertThatThrownBy(() -> service.loginWithBiometric(new AuthServiceUseCase.LoginWithBiometricInput(user(UamEnums.UserStatus.SUSPENDED))))
                .isInstanceOf(UamException.class);

        when(attempts.isLocked(any())).thenReturn(new LoginAttemptServiceUseCase.IsLockedOutput(true));
        assertThatThrownBy(() -> service.loginWithBiometric(new AuthServiceUseCase.LoginWithBiometricInput(user(UamEnums.UserStatus.ACTIVE))))
                .isInstanceOf(UamException.class).hasMessage(AuthConstants.LOGIN_LOCKED_MESSAGE);
        verify(sessions, never()).issue(any(), anyString(), anyString(), anyBoolean(), any());
    }
}
