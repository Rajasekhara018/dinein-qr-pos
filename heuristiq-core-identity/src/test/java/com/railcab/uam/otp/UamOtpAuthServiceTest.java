package com.heuristiq.uam.otp;

import com.heuristiq.uam.auth.LoginSessionService;
import com.heuristiq.uam.auth.MfaLoginGate;
import com.heuristiq.uam.common.UamException;
import com.heuristiq.uam.dto.LoginResponse;
import com.heuristiq.uam.dto.MfaChallengeRequiredResponse;
import com.heuristiq.uam.enums.UamEnums;
import com.heuristiq.uam.lockout.LoginAttemptServiceUseCase;
import com.heuristiq.uam.mfa.UserMfa;
import com.heuristiq.uam.mfa.UserMfaServiceUseCase;
import com.heuristiq.uam.notification.NmsNotificationService;
import com.heuristiq.uam.usercredentials.UserCredentialServiceUseCase;
import com.heuristiq.uam.users.UamUser;
import com.heuristiq.uam.users.repository.UamUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UamOtpAuthServiceTest {
    private static final String PHONE = "9876543210";

    private UamUserRepository userRepository;
    private LoginSessionService loginSessionService;
    private UserMfaServiceUseCase userMfaService;
    private LoginAttemptServiceUseCase loginAttemptService;
    private UamOtpAuthService service;
    private UamUser user;
    private LoginResponse session;

    @BeforeEach
    void setUp() {
        userRepository = mock(UamUserRepository.class);
        loginSessionService = mock(LoginSessionService.class);
        userMfaService = mock(UserMfaServiceUseCase.class);
        loginAttemptService = mock(LoginAttemptServiceUseCase.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("203.0.113.7");

        user = UamUser.builder().id("u1").userName("rider1").roles(List.of("CUSTOMER"))
                .status(UamEnums.UserStatus.ACTIVE).build();
        when(userRepository.findByPhone(PHONE)).thenReturn(Optional.of(user));
        session = new LoginResponse("jwt", "jwt", "jwt", "refresh", "u1", null, null, "rider1", null, null,
                UamEnums.UserType.CUSTOMER, "CUSTOMER", List.of("CUSTOMER"));
        when(loginSessionService.issue(any(), any(), any(), anyBoolean())).thenReturn(session);
        when(userMfaService.listEnabledByUser(any())).thenReturn(new UserMfaServiceUseCase.ListEnabledByUserOutput(List.of()));
        when(loginAttemptService.isLocked(any())).thenReturn(new LoginAttemptServiceUseCase.IsLockedOutput(false));

        OtpChallengeManager manager = new OtpChallengeManager(new InMemoryOtpStore(), 5, 5, 30, 5);
        MfaLoginGate gate = new MfaLoginGate(userMfaService, loginAttemptService);
        service = new UamOtpAuthService(userRepository, loginSessionService, manager, gate,
                mock(UserCredentialServiceUseCase.class), mock(NmsNotificationService.class), request,
                new com.heuristiq.uam.metrics.UamMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry()), 5, true);
    }

    private String sendOtp() {
        return service.sendPhoneOtp(new UamOtpAuthServiceUseCase.SendPhoneOtpInput(PHONE, UamEnums.UserType.CUSTOMER))
                .response().otp();
    }

    private UamOtpAuthServiceUseCase.VerifyPhoneOtpOutput submit(String otp, String totp) {
        return service.verifyPhoneOtp(new UamOtpAuthServiceUseCase.VerifyPhoneOtpInput(
                PHONE, otp, UamEnums.UserType.CUSTOMER, totp));
    }

    private void enrollMfa(boolean totpValid) {
        UserMfa mfa = UserMfa.builder().id("m1").userId("u1").mfaType(UamEnums.MfaType.TOTP).primary(true).enabled(true).build();
        when(userMfaService.listEnabledByUser(any())).thenReturn(new UserMfaServiceUseCase.ListEnabledByUserOutput(List.of(mfa)));
        when(userMfaService.verifyCode(any())).thenReturn(new UserMfaServiceUseCase.VerifyCodeOutput(totpValid));
    }

    @Test
    void userWithoutMfaKeepsSingleFactorOtpLogin() {
        String otp = sendOtp();

        assertThat(submit(otp, null).response()).isSameAs(session);
        verify(loginSessionService).issue(eq(user), any(), any(), eq(false));
    }

    @Test
    void userWithMfaGetsChallengeNotSessionWhenNoTotpIsSent() {
        enrollMfa(true);
        String otp = sendOtp();

        assertThat(submit(otp, null).response()).isInstanceOf(MfaChallengeRequiredResponse.class);
        verify(loginSessionService, never()).issue(any(), any(), any(), anyBoolean());
        MfaChallengeRequiredResponse challenge = (MfaChallengeRequiredResponse) submit(otp, null).response();
        assertThat(challenge.mfaRequired()).isTrue();
        assertThat(challenge.mfaType()).isEqualTo(UamEnums.MfaType.TOTP);
    }

    @Test
    void userWithMfaCompletesLoginByResubmittingOtpWithTotp() {
        enrollMfa(true);
        String otp = sendOtp();
        submit(otp, null);

        assertThat(submit(otp, "123456").response()).isSameAs(session);
        verify(loginSessionService).issue(eq(user), any(), any(), eq(true));
    }

    @Test
    void wrongTotpIsRejectedWithoutSessionAndBurnsTheOtp() {
        enrollMfa(false);
        String otp = sendOtp();

        assertThatThrownBy(() -> submit(otp, "000000"))
                .isInstanceOf(UamException.class).hasMessage("Invalid authenticator code");
        verify(loginSessionService, never()).issue(any(), any(), any(), anyBoolean());
        verify(loginAttemptService).recordFailure(any());

        enrollMfa(true);
        assertThatThrownBy(() -> submit(otp, "123456"))
                .isInstanceOf(UamException.class).hasMessage(OtpConstants.INVALID_OTP_MESSAGE);
    }

    @Test
    void wrongOtpNeverReachesTheMfaStep() {
        enrollMfa(true);
        sendOtp();

        assertThatThrownBy(() -> submit("000000", "123456"))
                .isInstanceOf(UamException.class).hasMessage(OtpConstants.INVALID_OTP_MESSAGE);
        verify(userMfaService, never()).verifyCode(any());
        verify(loginSessionService, never()).issue(any(), any(), any(), anyBoolean());
    }

    @Test
    void lockedOutAccountCannotUseOtpPlusTotp() {
        enrollMfa(true);
        when(loginAttemptService.isLocked(any())).thenReturn(new LoginAttemptServiceUseCase.IsLockedOutput(true));
        String otp = sendOtp();

        assertThatThrownBy(() -> submit(otp, "123456")).isInstanceOf(UamException.class);
        verify(loginSessionService, never()).issue(any(), any(), any(), anyBoolean());
    }

    @Test
    void fiveWrongOtpGuessesLockTheChallengeAndTheRightOtpNoLongerWorks() {
        String otp = sendOtp();
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> submit("000000", null)).hasMessage(OtpConstants.INVALID_OTP_MESSAGE);
        }
        assertThatThrownBy(() -> submit(otp, null)).hasMessage(OtpConstants.INVALID_OTP_MESSAGE);
        verify(loginSessionService, never()).issue(any(), any(), any(), anyBoolean());
    }

    @Test
    void otpCannotBeReplayedAfterSuccessfulLogin() {
        String otp = sendOtp();
        submit(otp, null);

        assertThatThrownBy(() -> submit(otp, null)).hasMessage(OtpConstants.INVALID_OTP_MESSAGE);
    }

    @Test
    void aRefusedApplicationDoesNotSpendTheCodeSoTheRetryWithAnotherApplicationWorks() {
        String otp = sendOtp();
        org.mockito.Mockito.doThrow(new com.heuristiq.uam.common.UamException("You do not have access to this application",
                org.springframework.http.HttpStatus.FORBIDDEN)).doNothing().when(loginSessionService).requireAccessToRequestedApplication(any());

        assertThatThrownBy(() -> submit(otp, null)).hasMessage("You do not have access to this application");
        verify(loginSessionService, never()).issue(any(), any(), any(), anyBoolean());

        assertThat(submit(otp, null).response()).isSameAs(session);
    }

    @Test
    void anInactiveAccountDoesNotSpendTheCodeEither() {
        String otp = sendOtp();
        user.setStatus(UamEnums.UserStatus.INACTIVE);

        assertThatThrownBy(() -> submit(otp, null)).hasMessage("User account is not active");

        user.setStatus(UamEnums.UserStatus.ACTIVE);
        assertThat(submit(otp, null).response()).isSameAs(session);
    }

    @Test
    void immediateResendIsRejectedWith429() {
        sendOtp();

        assertThatThrownBy(this::sendOtp).isInstanceOfSatisfying(UamException.class,
                e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
    }
}
