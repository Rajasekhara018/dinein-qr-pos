package com.heuristiq.uam.otp;

import com.heuristiq.uam.auth.LoginSessionService;
import com.heuristiq.uam.auth.MfaLoginGate;
import com.heuristiq.uam.common.UamException;
import com.heuristiq.uam.dto.LoginResponse;
import com.heuristiq.uam.dto.MfaChallengeRequiredResponse;
import com.heuristiq.uam.dto.OtpSentResponse;
import com.heuristiq.uam.enums.UamEnums;
import com.heuristiq.uam.helper.RName;
import com.heuristiq.uam.lockout.LoginAttemptServiceUseCase;
import com.heuristiq.uam.metrics.UamMetrics;
import com.heuristiq.uam.mfa.UserMfa;
import com.heuristiq.uam.mfa.UserMfaServiceUseCase;
import com.heuristiq.uam.notification.NmsNotificationService;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The email and password-reset halves of {@link UamOtpAuthService} (the phone/MFA flow is in UamOtpAuthServiceTest). */
class UamOtpAuthServiceEmailFlowTest {
    private static final String EMAIL = "rahul@example.com";

    private UamUserRepository userRepository;
    private LoginSessionService loginSessionService;
    private UserMfaServiceUseCase userMfaService;
    private LoginAttemptServiceUseCase loginAttemptService;
    private UserCredentialServiceUseCase credentials;
    private NmsNotificationService nms;
    private HttpServletRequest request;
    private UamUser user;
    private LoginResponse session;

    @BeforeEach
    void setUp() {
        userRepository = mock(UamUserRepository.class);
        loginSessionService = mock(LoginSessionService.class);
        userMfaService = mock(UserMfaServiceUseCase.class);
        loginAttemptService = mock(LoginAttemptServiceUseCase.class);
        credentials = mock(UserCredentialServiceUseCase.class);
        nms = mock(NmsNotificationService.class);
        request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("203.0.113.7");
        when(request.getHeader("User-Agent")).thenReturn("JUnit");

        user = UamUser.builder().id("u1").userName("rahul").email(EMAIL).roles(List.of("CUSTOMER"))
                .fullName(new RName("Rahul", null, "K")).status(UamEnums.UserStatus.ACTIVE).build();
        when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user));
        session = new LoginResponse("jwt", "jwt", "jwt", "refresh", "u1", null, null, "rahul", null, null,
                UamEnums.UserType.CUSTOMER, "CUSTOMER", List.of("CUSTOMER"));
        when(loginSessionService.issue(any(), any(), any(), anyBoolean())).thenReturn(session);
        when(userMfaService.listEnabledByUser(any())).thenReturn(new UserMfaServiceUseCase.ListEnabledByUserOutput(List.of()));
        when(loginAttemptService.isLocked(any())).thenReturn(new LoginAttemptServiceUseCase.IsLockedOutput(false));
    }

    private UamOtpAuthService service(boolean exposeOtp) {
        OtpChallengeManager manager = new OtpChallengeManager(new InMemoryOtpStore(), 5, 5, 30, 5);
        return new UamOtpAuthService(userRepository, loginSessionService, manager,
                new MfaLoginGate(userMfaService, loginAttemptService), credentials, nms, request,
                new UamMetrics(new SimpleMeterRegistry()), 5, exposeOtp);
    }

    @Test
    void emailOtpIsSentToTheAccountWithMaskedAddressAndOtpHiddenByDefault() {
        UamOtpAuthService service = service(false);

        OtpSentResponse sent = service.sendEmailOtp(new UamOtpAuthServiceUseCase.SendEmailOtpInput(" " + EMAIL + " ", UamEnums.UserType.CUSTOMER)).response();

        assertThat(sent.maskedEmail()).isEqualTo("ra***@example.com");
        assertThat(sent.maskedPhone()).isNull();
        assertThat(sent.expiresInMinutes()).isEqualTo(5);
        assertThat(sent.otp()).isNull();
        verify(nms).sendOtp(eq(EMAIL), eq(null), eq("Rahul K"), any(), eq(5), any());
    }

    @Test
    void shortEmailLocalPartIsMaskedWithAsterisk() {
        user.setEmail("ab@x.com");
        when(userRepository.findByEmailIgnoreCase("ab@x.com")).thenReturn(Optional.of(user));
        OtpSentResponse sent = service(false).sendEmailOtp(new UamOtpAuthServiceUseCase.SendEmailOtpInput("ab@x.com", UamEnums.UserType.CUSTOMER)).response();
        assertThat(sent.maskedEmail()).isEqualTo("a*@x.com");
    }

    @Test
    void emailLoginWithCorrectOtpIssuesSessionAndOtpCannotBeReused() {
        UamOtpAuthService service = service(true);
        String otp = service.sendEmailOtp(new UamOtpAuthServiceUseCase.SendEmailOtpInput(EMAIL, UamEnums.UserType.CUSTOMER)).response().otp();
        assertThat(otp).hasSize(6);

        var out = service.verifyEmailOtp(new UamOtpAuthServiceUseCase.VerifyEmailOtpInput(EMAIL, otp, UamEnums.UserType.CUSTOMER, null));

        assertThat(out.response()).isSameAs(session);
        verify(loginSessionService).issue(user, "203.0.113.7", "JUnit", false);
        assertThatThrownBy(() -> service.verifyEmailOtp(new UamOtpAuthServiceUseCase.VerifyEmailOtpInput(EMAIL, otp, UamEnums.UserType.CUSTOMER, null)))
                .isInstanceOf(UamException.class).hasMessage(OtpConstants.INVALID_OTP_MESSAGE);
    }

    @Test
    void aDriverSigningInToTheCustomerAppGetsTheCustomerRoleOnTheSameAccountBeforeTheSessionIsIssued() {
        user.setRoles(List.of("DRIVER"));
        when(userRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        UamOtpAuthService service = service(true);
        String otp = service.sendEmailOtp(new UamOtpAuthServiceUseCase.SendEmailOtpInput(EMAIL, UamEnums.UserType.CUSTOMER)).response().otp();

        service.verifyEmailOtp(new UamOtpAuthServiceUseCase.VerifyEmailOtpInput(EMAIL, otp, UamEnums.UserType.CUSTOMER, null));

        assertThat(user.getRoles()).containsExactly("DRIVER", "CUSTOMER");
        var order = org.mockito.Mockito.inOrder(userRepository, loginSessionService);
        order.verify(userRepository).save(user);
        order.verify(loginSessionService).issue(user, "203.0.113.7", "JUnit", false);
    }

    @Test
    void aWrongCodeGrantsNothingAndTheDriverRoleIsNeverSelfGranted() {
        user.setRoles(List.of("DRIVER"));
        UamOtpAuthService service = service(true);
        service.sendEmailOtp(new UamOtpAuthServiceUseCase.SendEmailOtpInput(EMAIL, UamEnums.UserType.CUSTOMER));
        assertThatThrownBy(() -> service.verifyEmailOtp(new UamOtpAuthServiceUseCase.VerifyEmailOtpInput(EMAIL, "000000x", UamEnums.UserType.CUSTOMER, null)))
                .isInstanceOf(UamException.class);
        assertThat(user.getRoles()).containsExactly("DRIVER");

        // A customer signing in to the driver app does not become a driver (that needs onboarding and approval).
        user.setRoles(List.of("CUSTOMER"));
        String otp = service.sendEmailOtp(new UamOtpAuthServiceUseCase.SendEmailOtpInput(EMAIL, UamEnums.UserType.DRIVER)).response().otp();
        service.verifyEmailOtp(new UamOtpAuthServiceUseCase.VerifyEmailOtpInput(EMAIL, otp, UamEnums.UserType.DRIVER, null));
        assertThat(user.getRoles()).containsExactly("CUSTOMER");
        verify(userRepository, never()).save(any());
    }

    @Test
    void anAccountThatIsAlreadyACustomerIsNotSavedAgain() {
        UamOtpAuthService service = service(true);
        String otp = service.sendEmailOtp(new UamOtpAuthServiceUseCase.SendEmailOtpInput(EMAIL, UamEnums.UserType.CUSTOMER)).response().otp();
        service.verifyEmailOtp(new UamOtpAuthServiceUseCase.VerifyEmailOtpInput(EMAIL, otp, UamEnums.UserType.CUSTOMER, null));
        assertThat(user.getRoles()).containsExactly("CUSTOMER");
        verify(userRepository, never()).save(any());
    }

    @Test
    void emailLoginWithMfaEnrolledAndNoTotpReturnsChallenge() {
        UserMfa mfa = UserMfa.builder().id("m").userId("u1").mfaType(UamEnums.MfaType.TOTP).primary(true).enabled(true).build();
        when(userMfaService.listEnabledByUser(any())).thenReturn(new UserMfaServiceUseCase.ListEnabledByUserOutput(List.of(mfa)));
        UamOtpAuthService service = service(true);
        String otp = service.sendEmailOtp(new UamOtpAuthServiceUseCase.SendEmailOtpInput(EMAIL, UamEnums.UserType.CUSTOMER)).response().otp();

        var out = service.verifyEmailOtp(new UamOtpAuthServiceUseCase.VerifyEmailOtpInput(EMAIL, otp, UamEnums.UserType.CUSTOMER, null));

        assertThat(out.response()).isInstanceOf(MfaChallengeRequiredResponse.class);
        verify(loginSessionService, never()).issue(any(), any(), any(), anyBoolean());
    }

    @Test
    void anUnknownEmailGetsTheSameAnswersAsARealOneAndNothingIsSent() {
        UamOtpAuthService service = service(true);
        when(userRepository.findByEmailIgnoreCase("ghost@x.com")).thenReturn(Optional.empty());

        // Send: the same "code sent" shape as for a real account (masked address, expiry), no code, nothing delivered.
        OtpSentResponse sent = service.sendEmailOtp(new UamOtpAuthServiceUseCase.SendEmailOtpInput("ghost@x.com", UamEnums.UserType.CUSTOMER)).response();
        assertThat(sent.maskedEmail()).isNotBlank().doesNotContain("ghost@x.com");
        assertThat(sent.maskedPhone()).isNull();
        assertThat(sent.otp()).isNull();
        // Verify: the same message as a wrong code.
        assertThatThrownBy(() -> service.verifyEmailOtp(new UamOtpAuthServiceUseCase.VerifyEmailOtpInput("ghost@x.com", "123456", UamEnums.UserType.CUSTOMER, null)))
                .hasMessage(OtpConstants.INVALID_OTP_MESSAGE);
        verify(loginSessionService, never()).issue(any(), any(), any(), anyBoolean());
        assertThatThrownBy(() -> service.sendEmailOtp(new UamOtpAuthServiceUseCase.SendEmailOtpInput(" ", UamEnums.UserType.CUSTOMER)))
                .hasMessage("Email is required");
        verify(nms, never()).sendOtp(any(), any(), any(), any(), anyInt(), any());
    }

    @Test
    void phoneOtpIsMaskedToLastFourDigitsAndRejectsWrongUserType() {
        when(userRepository.findByPhone("9876543210")).thenReturn(Optional.of(user));
        UamOtpAuthService service = service(true);

        OtpSentResponse sent = service.sendPhoneOtp(new UamOtpAuthServiceUseCase.SendPhoneOtpInput("9876543210", UamEnums.UserType.CUSTOMER)).response();
        assertThat(sent.maskedPhone()).isEqualTo("******3210");
        assertThat(sent.maskedEmail()).isNull();

        // The phone has no DRIVER account: same answer as a real one, but no code is generated for the caller or delivered.
        org.mockito.Mockito.clearInvocations(nms);
        OtpSentResponse other = service.sendPhoneOtp(new UamOtpAuthServiceUseCase.SendPhoneOtpInput("9876543210", UamEnums.UserType.DRIVER)).response();
        assertThat(other.maskedPhone()).isEqualTo("******3210");
        assertThat(other.otp()).isNull();
        verify(nms, never()).sendOtp(any(), any(), any(), any(), anyInt(), any());
        assertThatThrownBy(() -> service.verifyPhoneOtp(new UamOtpAuthServiceUseCase.VerifyPhoneOtpInput("9876543210", "123456", UamEnums.UserType.DRIVER, null)))
                .hasMessage(OtpConstants.INVALID_OTP_MESSAGE);
        assertThatThrownBy(() -> service.sendPhoneOtp(new UamOtpAuthServiceUseCase.SendPhoneOtpInput(" ", UamEnums.UserType.CUSTOMER)))
                .hasMessage("Phone is required");
    }

    @Test
    void resetPasswordRequiresValidOtpThenSetsPermanentPassword() {
        UamOtpAuthService service = service(true);
        String otp = service.sendEmailOtp(new UamOtpAuthServiceUseCase.SendEmailOtpInput(EMAIL, UamEnums.UserType.CUSTOMER)).response().otp();

        assertThatThrownBy(() -> service.resetPassword(new UamOtpAuthServiceUseCase.ResetPasswordInput(EMAIL, "000000".equals(otp) ? "111111" : "000000", "NewPass#123", UamEnums.UserType.CUSTOMER)))
                .hasMessage(OtpConstants.INVALID_OTP_MESSAGE);
        verify(credentials, never()).setPassword(any());

        service.resetPassword(new UamOtpAuthServiceUseCase.ResetPasswordInput(EMAIL, otp, "NewPass#123", UamEnums.UserType.CUSTOMER));

        verify(credentials).setPassword(new UserCredentialServiceUseCase.SetPasswordInput("u1", "NewPass#123", false, true));
    }

    @Test
    void resetPasswordForUnknownEmailFails() {
        when(userRepository.findByEmailIgnoreCase("ghost@x.com")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service(true).resetPassword(new UamOtpAuthServiceUseCase.ResetPasswordInput("ghost@x.com", "1", "pw", UamEnums.UserType.CUSTOMER)))
                .hasMessage(OtpConstants.INVALID_OTP_MESSAGE);
        verify(credentials, never()).setPassword(any());
    }

    @Test
    void anUnknownAddressIsThrottledLikeARealOne() {
        when(userRepository.findByEmailIgnoreCase("ghost@x.com")).thenReturn(Optional.empty());
        UamOtpAuthService service = service(true);
        var input = new UamOtpAuthServiceUseCase.SendEmailOtpInput("ghost@x.com", UamEnums.UserType.CUSTOMER);
        // The send limits apply per address whether or not an account exists, so a 429 never tells them apart.
        assertThatThrownBy(() -> {
            for (int i = 0; i < 50; i++) {
                service.sendEmailOtp(input);
            }
        }).isInstanceOfSatisfying(com.heuristiq.uam.common.UamException.class,
                e -> assertThat(e.getStatus()).isEqualTo(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS));
    }

    @Test
    void loginPayloadRejectsInactiveUsersAndHonoursMfaFlag() {
        UamOtpAuthService service = service(true);

        assertThat(service.loginPayload(new UamOtpAuthServiceUseCase.LoginPayloadInput(user, true)).response()).isSameAs(session);
        verify(loginSessionService).issue(user, "203.0.113.7", "JUnit", true);

        user.setStatus(UamEnums.UserStatus.SUSPENDED);
        assertThatThrownBy(() -> service.loginPayload(new UamOtpAuthServiceUseCase.LoginPayloadInput(user)))
                .isInstanceOf(UamException.class);
    }

    @Test
    void anAccountWithoutNameIsAddressedGenerically() {
        user.setFullName(null);
        service(true).sendEmailOtp(new UamOtpAuthServiceUseCase.SendEmailOtpInput(EMAIL, UamEnums.UserType.CUSTOMER));
        verify(nms).sendOtp(eq(EMAIL), eq(null), eq("there"), any(), eq(5), any());
    }
}
