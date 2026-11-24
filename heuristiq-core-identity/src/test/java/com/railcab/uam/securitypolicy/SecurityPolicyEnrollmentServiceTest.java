package com.heuristiq.uam.securitypolicy;

import com.heuristiq.uam.auth.LoginSessionService;
import com.heuristiq.uam.common.UamException;
import com.heuristiq.uam.config.UamProperties;
import com.heuristiq.uam.enums.UamEnums;
import com.heuristiq.uam.lockout.LoginAttemptServiceUseCase;
import com.heuristiq.uam.mfa.*;
import com.heuristiq.uam.security.JwtService;
import com.heuristiq.uam.users.UamUser;
import com.heuristiq.uam.users.repository.UamUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SecurityPolicyEnrollmentServiceTest {
    final UamUserRepository users = mock(UamUserRepository.class);
    final UserMfaServiceUseCase mfa = mock(UserMfaServiceUseCase.class);
    final LoginAttemptServiceUseCase attempts = mock(LoginAttemptServiceUseCase.class);
    final LoginSessionService sessions = mock(LoginSessionService.class);
    final HttpServletRequest request = mock(HttpServletRequest.class);
    final UamUser user = UamUser.builder().id("user").userName("alice").status(UamEnums.UserStatus.ACTIVE).build();
    final JwtService jwt;
    final SecurityPolicyEnrollmentService enrollment;
    SecurityPolicyEnrollmentServiceTest() {
        var props = new UamProperties();
        props.getAuth().setJwtSecret("a-long-test-secret-with-at-least-thirty-two-bytes!!");
        jwt = new JwtService(props);
        enrollment = new SecurityPolicyEnrollmentService(jwt, users, mfa, attempts, sessions, request);
        when(users.findById("user")).thenReturn(Optional.of(user));
        when(attempts.isLocked(any())).thenReturn(new LoginAttemptServiceUseCase.IsLockedOutput(false));
    }
    @Test void proofIsShortLivedAndCannotBeUsedAsAFullSession() {
        var challenge = enrollment.challenge(user);
        var claims = jwt.parse(challenge.enrollmentToken());
        assertThat(claims.get("purpose")).isEqualTo("MFA_ENROLLMENT");
        assertThat(claims).doesNotContainKeys("sid", "roles", "apps");
        assertThat(claims.getExpiration().getTime() - claims.getIssuedAt().getTime()).isEqualTo(300000L);
        assertThat(challenge.mfaEnrollmentRequired()).isTrue();
        verifyNoInteractions(sessions);
    }
    @Test void enrollmentProofCanOnlyEnrollItsOwnAccountAndDoesNotIssueASession() {
        String proof = enrollment.challenge(user).enrollmentToken();
        when(mfa.enrollTotp(any())).thenReturn(new UserMfaServiceUseCase.EnrollTotpOutput(new MfaEnrollTotpResponse("m", "secret", "png", "uri")));
        assertThat(enrollment.begin(proof).id()).isEqualTo("m");
        verify(mfa).enrollTotp(new UserMfaServiceUseCase.EnrollTotpInput("user"));
        verifyNoInteractions(sessions);
        when(mfa.get(any())).thenReturn(new UserMfaServiceUseCase.GetOutput(UserMfa.builder().id("m").userId("other").mfaType(UamEnums.MfaType.TOTP).build()));
        assertThatThrownBy(() -> enrollment.complete(proof, "m", "123456")).hasMessageContaining("invalid");
        verify(mfa, never()).verifyEnrollment(any());
    }
    @Test void onlySuccessfulVerificationIssuesASessionAndAConsumedEnrollmentCannotBeReplayed() {
        String proof = enrollment.challenge(user).enrollmentToken();
        var method = UserMfa.builder().id("m").userId("user").mfaType(UamEnums.MfaType.TOTP).verified(false).build();
        when(mfa.get(any())).thenReturn(new UserMfaServiceUseCase.GetOutput(method));
        when(mfa.verifyEnrollment(any())).thenThrow(new UamException("Invalid code"));
        assertThatThrownBy(() -> enrollment.complete(proof, "m", "wrong")).hasMessageContaining("Invalid code");
        verifyNoInteractions(sessions);
        verify(attempts).recordFailure(new LoginAttemptServiceUseCase.RecordFailureInput("alice"));
        doAnswer(i -> { method.setVerified(true); return new UserMfaServiceUseCase.VerifyEnrollmentOutput(method); }).when(mfa).verifyEnrollment(any());
        enrollment.complete(proof, "m", "123456");
        verify(sessions).issue(eq(user), any(), any(), eq(true));
        assertThatThrownBy(() -> enrollment.complete(proof, "m", "123456")).hasMessageContaining("invalid");
    }
    @Test void invalidLockedOrChangedScopeProofsAreRefused() {
        assertThatThrownBy(() -> enrollment.begin("invalid")).hasMessageContaining("invalid");
        String proof = enrollment.challenge(user).enrollmentToken();
        when(attempts.isLocked(any())).thenReturn(new LoginAttemptServiceUseCase.IsLockedOutput(true));
        assertThatThrownBy(() -> enrollment.begin(proof)).hasMessageContaining("Too many failed");
        when(attempts.isLocked(any())).thenReturn(new LoginAttemptServiceUseCase.IsLockedOutput(false));
        when(request.getHeader(com.heuristiq.uam.auth.AuthConstants.APPLICATION_CODE_HEADER)).thenReturn("other");
        assertThatThrownBy(() -> enrollment.begin(proof)).hasMessageContaining("invalid");
    }
}
