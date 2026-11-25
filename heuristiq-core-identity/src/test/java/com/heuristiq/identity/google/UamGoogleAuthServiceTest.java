package com.heuristiq.identity.google;

import com.heuristiq.identity.auth.LoginSessionService;
import com.heuristiq.identity.auth.MfaLoginGate;
import com.heuristiq.identity.common.UamException;
import com.heuristiq.identity.dto.LoginResponse;
import com.heuristiq.identity.dto.MfaChallengeRequiredResponse;
import com.heuristiq.identity.enums.UamEnums;
import com.heuristiq.identity.identity.IdentityProviderSyncService;
import com.heuristiq.identity.lockout.LoginAttemptServiceUseCase;
import com.heuristiq.identity.mfa.UserMfa;
import com.heuristiq.identity.mfa.UserMfaServiceUseCase;
import com.heuristiq.identity.usercredentials.UserCredential;
import com.heuristiq.identity.usercredentials.UserCredentialRepository;
import com.heuristiq.identity.users.UamUser;
import com.heuristiq.identity.users.repository.UamUserRepository;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UamGoogleAuthServiceTest {
    private UserMfaServiceUseCase userMfaService;
    private LoginSessionService loginSessionService;
    private UamUserRepository userRepository;
    private UamGoogleAuthService service;
    private LoginResponse session;
    private HttpServletRequest request;
    private UserCredentialRepository credentialRepository;
    private GoogleIdentityTokenVerifier verifier;

    @BeforeEach
    void setUp() {
        userRepository = mock(UamUserRepository.class);
        credentialRepository = mock(UserCredentialRepository.class);
        verifier = mock(GoogleIdentityTokenVerifier.class);
        loginSessionService = mock(LoginSessionService.class);
        userMfaService = mock(UserMfaServiceUseCase.class);
        LoginAttemptServiceUseCase loginAttemptService = mock(LoginAttemptServiceUseCase.class);
        request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("203.0.113.7");

        UamUser user = UamUser.builder().id("u1").userName("rider1@example.com").email("rider1@example.com")
                .roles(List.of("CUSTOMER")).status(UamEnums.UserStatus.ACTIVE).build();
        when(verifier.verify("id-token")).thenReturn(new GoogleIdentity("uid-1", "rider1@example.com", "Rider One", null));
        when(credentialRepository.findByProviderAndProviderUserId(any(), eq("uid-1")))
                .thenReturn(Optional.of(UserCredential.builder().userId("u1").build()));
        when(userRepository.findById("u1")).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        session = new LoginResponse("jwt", "jwt", "jwt", "refresh", "u1", null, null, "rider1", null, null,
                UamEnums.UserType.CUSTOMER, "CUSTOMER", List.of("CUSTOMER"));
        when(loginSessionService.issue(any(), any(), any(), anyBoolean())).thenReturn(session);
        when(userMfaService.listEnabledByUser(any())).thenReturn(new UserMfaServiceUseCase.ListEnabledByUserOutput(List.of()));
        when(loginAttemptService.isLocked(any())).thenReturn(new LoginAttemptServiceUseCase.IsLockedOutput(false));

        service = new UamGoogleAuthService(userRepository, credentialRepository, verifier, loginSessionService,
                new MfaLoginGate(userMfaService, loginAttemptService), mock(IdentityProviderSyncService.class), request);
    }

    private void enrollMfa(boolean totpValid) {
        UserMfa mfa = UserMfa.builder().id("m1").userId("u1").mfaType(UamEnums.MfaType.TOTP).primary(true).enabled(true).build();
        when(userMfaService.listEnabledByUser(any())).thenReturn(new UserMfaServiceUseCase.ListEnabledByUserOutput(List.of(mfa)));
        when(userMfaService.verifyCode(any())).thenReturn(new UserMfaServiceUseCase.VerifyCodeOutput(totpValid));
    }

    private UamGoogleAuthServiceUseCase.LoginWithTypeOutput login(String totp) {
        return service.login(new UamGoogleAuthServiceUseCase.LoginWithTypeInput("id-token", UamEnums.UserType.CUSTOMER, totp));
    }

    /** A Google identity whose email has no account and no linked Google credential. */
    private void unknownGoogleEmail() {
        when(verifier.verify("new-token")).thenReturn(new GoogleIdentity("uid-new", "new@example.com", "New Person", null));
        when(credentialRepository.findByProviderAndProviderUserId(any(), eq("uid-new"))).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("new@example.com")).thenReturn(Optional.empty());
    }

    @Test
    void anApplicationThatNamesItselfIsToldThereIsNoAccountAndNothingIsCreated() {
        unknownGoogleEmail();
        when(request.getHeader("X-Application-Code")).thenReturn("PASUPU_KUMKUMA_MATRIMONY_APP");

        assertThatThrownBy(() -> service.login(new UamGoogleAuthServiceUseCase.LoginWithTypeInput("new-token", UamEnums.UserType.CUSTOMER)))
                .isInstanceOfSatisfying(GoogleAuthException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN);
                    assertThat(e.getMessage()).isEqualTo("No account found for this Google email. Please register first.");
                });
        verify(userRepository, never()).save(any());
        verify(credentialRepository, never()).save(any());
    }

    @Test
    void aFirstGoogleSignInNeverCreatesAStaffAccountFromTheRequestedType() {
        unknownGoogleEmail();
        when(request.getHeader("X-Application-Code")).thenReturn(null);

        for (UamEnums.UserType staff : new UamEnums.UserType[] {
                UamEnums.UserType.SUPER_ADMIN, UamEnums.UserType.ADMIN, UamEnums.UserType.SECURITY, null}) {
            assertThatThrownBy(() -> service.login(new UamGoogleAuthServiceUseCase.LoginWithTypeInput("new-token", staff)))
                    .isInstanceOfSatisfying(UamException.class,
                            e -> assertThat(e.getStatus()).isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN));
        }
        verify(userRepository, never()).save(any());
        verify(credentialRepository, never()).save(any());
    }

    @Test
    void aFirstGoogleSignInFromTheBackOfficeStillCreatesAnOpsAccount() {
        unknownGoogleEmail();
        when(request.getHeader("X-Application-Code")).thenReturn(null);

        service.login(new UamGoogleAuthServiceUseCase.LoginWithTypeInput("new-token", UamEnums.UserType.OPS));

        verify(userRepository, org.mockito.Mockito.atLeastOnce()).save(org.mockito.ArgumentMatchers.argThat(
                (UamUser u) -> u.getRoles() != null && u.getRoles().equals(java.util.List.of("OPS"))));
    }

    @Test
    void aCallerThatNamesNoApplicationStillGetsAnAccountCreated() {
        unknownGoogleEmail();
        when(request.getHeader("X-Application-Code")).thenReturn(null);

        service.login(new UamGoogleAuthServiceUseCase.LoginWithTypeInput("new-token", UamEnums.UserType.CUSTOMER));

        verify(userRepository, org.mockito.Mockito.atLeastOnce()).save(any());
    }

    @Test
    void aKnownEmailStillSignsInWhenTheApplicationNamesItself() {
        when(request.getHeader("X-Application-Code")).thenReturn("PASUPU_KUMKUMA_MATRIMONY_APP");

        assertThat(login(null).response()).isSameAs(session);
    }

    @Test
    void aDriverSigningInToTheCustomerAppWithGoogleGetsTheCustomerRoleInTheIssuedSession() {
        UamUser driver = UamUser.builder().id("u1").userName("rider1@example.com").email("rider1@example.com")
                .roles(List.of("DRIVER")).status(UamEnums.UserStatus.ACTIVE).build();
        when(userRepository.findById("u1")).thenReturn(Optional.of(driver));

        login(null);

        org.mockito.ArgumentCaptor<UamUser> issued = org.mockito.ArgumentCaptor.forClass(UamUser.class);
        verify(loginSessionService).issue(issued.capture(), any(), any(), eq(false));
        assertThat(issued.getValue().getRoles()).containsExactly("DRIVER", "CUSTOMER");
    }

    @Test
    void aDriverRoleIsNeverSelfGrantedThroughGoogle() {
        login(null); // the set-up account is a CUSTOMER
        service.login(new UamGoogleAuthServiceUseCase.LoginWithTypeInput("id-token", UamEnums.UserType.DRIVER, null));
        org.mockito.ArgumentCaptor<UamUser> issued = org.mockito.ArgumentCaptor.forClass(UamUser.class);
        verify(loginSessionService, org.mockito.Mockito.times(2)).issue(issued.capture(), any(), any(), eq(false));
        assertThat(issued.getValue().getRoles()).containsExactly("CUSTOMER");
    }

    @Test
    void userWithoutMfaKeepsCurrentGoogleLogin() {
        assertThat(login(null).response()).isSameAs(session);
        verify(loginSessionService).issue(any(), any(), any(), eq(false));
    }

    @Test
    void twoArgumentInputStillWorksForExistingCallers() {
        assertThat(service.login(new UamGoogleAuthServiceUseCase.LoginWithTypeInput("id-token", UamEnums.UserType.CUSTOMER))
                .response()).isSameAs(session);
    }

    @Test
    void userWithMfaGetsChallengeInsteadOfSession() {
        enrollMfa(true);

        assertThat(login(null).response()).isInstanceOf(MfaChallengeRequiredResponse.class);
        verify(loginSessionService, never()).issue(any(), any(), any(), anyBoolean());
        verify(userRepository, never()).save(any());
    }

    @Test
    void userWithMfaGetsSessionMarkedMfaVerifiedWithValidTotp() {
        enrollMfa(true);

        assertThat(login("123456").response()).isSameAs(session);
        verify(loginSessionService).issue(any(), any(), any(), eq(true));
    }

    @Test
    void wrongTotpIsRejectedWithoutSession() {
        enrollMfa(false);

        assertThatThrownBy(() -> login("000000")).isInstanceOf(UamException.class)
                .hasMessage("Invalid authenticator code");
        verify(loginSessionService, never()).issue(any(), any(), any(), anyBoolean());
    }
}
