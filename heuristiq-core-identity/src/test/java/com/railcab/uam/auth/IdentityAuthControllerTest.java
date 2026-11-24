package com.heuristiq.uam.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heuristiq.uam.common.CommonRequestMapper;
import com.heuristiq.uam.common.CommonRequestObject;
import com.heuristiq.uam.common.CommonResponseObject;
import com.heuristiq.uam.common.RequestValidationException;
import com.heuristiq.uam.dto.LoginRequest;
import com.heuristiq.uam.dto.LoginResponse;
import com.heuristiq.uam.dto.LoginResult;
import com.heuristiq.uam.dto.OtpSentResponse;
import com.heuristiq.uam.dto.ProviderLoginRequest;
import com.heuristiq.uam.dto.ProviderRefreshRequest;
import com.heuristiq.uam.dto.RegistrationResponse;
import com.heuristiq.uam.enums.UamEnums;
import com.heuristiq.uam.google.UamGoogleAuthServiceUseCase;
import com.heuristiq.uam.identity.IdentityProviderAuthRouterUseCase;
import com.heuristiq.uam.otp.UamOtpAuthServiceUseCase;
import com.heuristiq.uam.refreshToken.RefreshTokenServiceUseCase;
import com.heuristiq.uam.registration.UserRegistrationServiceUseCase;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IdentityAuthControllerTest {
    private AuthServiceUseCase authService;
    private UamOtpAuthServiceUseCase otp;
    private UamGoogleAuthServiceUseCase google;
    private IdentityProviderAuthRouterUseCase router;
    private RefreshTokenServiceUseCase refreshTokens;
    private UserRegistrationServiceUseCase registration;
    private IdentityAuthController controller;
    private LoginResponse loginResponse;

    @BeforeEach
    void setUp() {
        authService = mock(AuthServiceUseCase.class);
        otp = mock(UamOtpAuthServiceUseCase.class);
        google = mock(UamGoogleAuthServiceUseCase.class);
        router = mock(IdentityProviderAuthRouterUseCase.class);
        refreshTokens = mock(RefreshTokenServiceUseCase.class);
        registration = mock(UserRegistrationServiceUseCase.class);
        CommonRequestMapper mapper = new CommonRequestMapper(new ObjectMapper(),
                Validation.buildDefaultValidatorFactory().getValidator());
        controller = new IdentityAuthController(authService, otp, google, router, refreshTokens, registration, mapper);
        loginResponse = mock(LoginResponse.class);
    }

    private static CommonRequestObject body(Map<String, Object> payload) {
        CommonRequestObject request = new CommonRequestObject();
        request.setRequestObject(payload);
        return request;
    }

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put((String) kv[i], kv[i + 1]);
        }
        return map;
    }

    @Test
    void localLoginUsesUsernamePasswordAndTotp() {
        when(authService.login(any())).thenReturn(new AuthServiceUseCase.LoginOutput(loginResponse));

        ResponseEntity<CommonResponseObject> out = controller.login(body(map("username", "jane", "password", "pw", "totpCode", "123456")));

        assertThat(out.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(out.getBody().getResponseObject()).isSameAs(loginResponse);
        ArgumentCaptor<AuthServiceUseCase.LoginInput> captor = ArgumentCaptor.forClass(AuthServiceUseCase.LoginInput.class);
        verify(authService).login(captor.capture());
        assertThat(captor.getValue().request()).isEqualTo(new LoginRequest("jane", "pw", "123456"));
        verify(google, never()).login(any(UamGoogleAuthServiceUseCase.LoginWithTypeInput.class));
    }

    @Test
    void googleTokenSelectsGoogleLogin() {
        LoginResult result = loginResponse;
        when(google.login(any(UamGoogleAuthServiceUseCase.LoginWithTypeInput.class)))
                .thenReturn(new UamGoogleAuthServiceUseCase.LoginWithTypeOutput(result));

        ResponseEntity<CommonResponseObject> out = controller.login(body(map("idToken", "gid", "userType", "CUSTOMER", "totpCode", "1")));

        assertThat(out.getBody().getResponseObject()).isSameAs(result);
        ArgumentCaptor<UamGoogleAuthServiceUseCase.LoginWithTypeInput> captor =
                ArgumentCaptor.forClass(UamGoogleAuthServiceUseCase.LoginWithTypeInput.class);
        verify(google).login(captor.capture());
        assertThat(captor.getValue().idToken()).isEqualTo("gid");
        assertThat(captor.getValue().userType()).isEqualTo(UamEnums.UserType.CUSTOMER);
        verify(authService, never()).login(any());
    }

    @Test
    void providerTypeSelectsExternalIdpLogin() {
        when(router.login(any())).thenReturn(new IdentityProviderAuthRouterUseCase.LoginOutput(loginResponse));

        ResponseEntity<CommonResponseObject> out = controller.login(body(map("providerType", "keycloak", "username", "u",
                "password", "p", "grantType", "password", "totp", "9", "userType", "ADMIN")));

        assertThat(out.getBody().getResponseObject()).isSameAs(loginResponse);
        ArgumentCaptor<IdentityProviderAuthRouterUseCase.LoginInput> captor =
                ArgumentCaptor.forClass(IdentityProviderAuthRouterUseCase.LoginInput.class);
        verify(router).login(captor.capture());
        ProviderLoginRequest sent = captor.getValue().request();
        assertThat(sent.providerType()).isEqualTo("keycloak");
        assertThat(sent.username()).isEqualTo("u");
        assertThat(sent.userType()).isEqualTo(UamEnums.UserType.ADMIN);
    }

    @Test
    void loginWithoutPayloadIsRejected() {
        assertThatThrownBy(() -> controller.login(new CommonRequestObject()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void registerReturns201AndValidatesPayload() {
        RegistrationResponse response = mock(RegistrationResponse.class);
        when(registration.register(any())).thenReturn(new UserRegistrationServiceUseCase.RegisterOutput(response));

        ResponseEntity<CommonResponseObject> out = controller.register(body(map("name", "Jane", "mobile", "9876543210",
                "email", "j@x.com", "city", "Pune", "userType", "CUSTOMER")));

        assertThat(out.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(out.getBody().getResponseObject()).isSameAs(response);

        assertThatThrownBy(() -> controller.register(body(map("name", "", "mobile", "9876543210", "email", "bad", "userType", "CUSTOMER"))))
                .isInstanceOf(RequestValidationException.class);
    }

    @Test
    void registerRejectsANameWithDigitsOrSymbolsAndNeverCreatesTheAccount() {
        for (String name : new String[] {"Ravi123", "4Ravi", "Ravi_Kumar", "R@vi", ".Ravi", "A"}) {
            assertThatThrownBy(() -> controller.register(body(map("name", name, "mobile", "9876543210",
                    "email", "r@x.com", "userType", "DRIVER"))))
                    .as(name).isInstanceOf(RequestValidationException.class)
                    .satisfies(e -> assertThat(((RequestValidationException) e).getErrors())
                            .singleElement()
                            .satisfies(err -> assertThat(err.field()).isEqualTo("name")));
        }
        assertThatThrownBy(() -> controller.register(body(map("name", "Ravi 2", "mobile", "9876543210",
                "email", "r@x.com", "userType", "DRIVER"))))
                .isInstanceOf(RequestValidationException.class)
                .satisfies(e -> assertThat(((RequestValidationException) e).getErrors().get(0).message())
                        .isEqualTo("Name may contain only letters, spaces, '.', apostrophe and hyphen."));
        verify(registration, never()).register(any());
    }

    @Test
    void registerAcceptsNamesInIndianScriptsAndWithPunctuation() {
        RegistrationResponse response = mock(RegistrationResponse.class);
        when(registration.register(any())).thenReturn(new UserRegistrationServiceUseCase.RegisterOutput(response));

        for (String name : new String[] {"राम कुमार", "முருகன்", "O'Brien", "O’Brien", "Jean-Luc", "A. Kumar", "  Jo  "}) {
            ResponseEntity<CommonResponseObject> out = controller.register(body(map("name", name, "mobile", "9876543210",
                    "email", "r@x.com", "userType", "DRIVER")));
            assertThat(out.getStatusCode()).as(name).isEqualTo(HttpStatus.CREATED);
        }
    }

    @Test
    void publicRegistrationRefusesEveryStaffAccountTypeAndNeverCreatesTheAccount() {
        for (String staff : new String[] {"SUPER_ADMIN", "ADMIN", "OPS", "SECURITY"}) {
            assertThatThrownBy(() -> controller.register(body(map("name", "Eve", "mobile", "9876543210",
                    "email", "eve@x.com", "city", "Pune", "userType", staff))))
                    .as(staff).isInstanceOf(com.heuristiq.uam.common.UamException.class)
                    .extracting(e -> ((com.heuristiq.uam.common.UamException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        }
        org.mockito.Mockito.verify(registration, org.mockito.Mockito.never()).register(any());
    }

    @Test
    void phoneOtpSendMessageMentionsMaskedPhoneAndExpiry() {
        when(otp.sendPhoneOtp(any())).thenReturn(new UamOtpAuthServiceUseCase.SendPhoneOtpOutput(
                new OtpSentResponse(null, "******4321", 5, null)));

        CommonResponseObject out = controller.sendPhoneOtp(body(map("phone", "9876543210", "userType", "CUSTOMER"))).getBody();

        assertThat(out.getMessage()).isEqualTo("A verification code has been sent to ******4321. It expires in 5 minutes.");
    }

    @Test
    void emailOtpSendMessageHandlesSingularMinuteAndMissingDestinationOrExpiry() {
        when(otp.sendEmailOtp(any())).thenReturn(
                new UamOtpAuthServiceUseCase.SendEmailOtpOutput(new OtpSentResponse("ra***@x.com", null, 1, null)),
                new UamOtpAuthServiceUseCase.SendEmailOtpOutput(new OtpSentResponse(null, null, null, null)));

        assertThat(controller.sendEmailOtp(body(map("email", "r@x.com", "userType", "CUSTOMER"))).getBody().getMessage())
                .isEqualTo("A verification code has been sent to ra***@x.com. It expires in 1 minute.");
        assertThat(controller.sendEmailOtp(body(map("email", "r@x.com", "userType", "CUSTOMER"))).getBody().getMessage())
                .isEqualTo("A verification code has been sent to your registered contact.");
    }

    @Test
    void verifyOtpEndpointsPassCodesThrough() {
        LoginResult result = loginResponse;
        when(otp.verifyPhoneOtp(any())).thenReturn(new UamOtpAuthServiceUseCase.VerifyPhoneOtpOutput(result));
        when(otp.verifyEmailOtp(any())).thenReturn(new UamOtpAuthServiceUseCase.VerifyEmailOtpOutput(result));

        assertThat(controller.verifyPhoneOtp(body(map("phone", "9876543210", "otp", "111111", "userType", "CUSTOMER", "totpCode", "7")))
                .getBody().getResponseObject()).isSameAs(result);
        assertThat(controller.verifyEmailOtp(body(map("email", "r@x.com", "otp", "222222", "userType", "DRIVER")))
                .getBody().getResponseObject()).isSameAs(result);

        ArgumentCaptor<UamOtpAuthServiceUseCase.VerifyPhoneOtpInput> phone =
                ArgumentCaptor.forClass(UamOtpAuthServiceUseCase.VerifyPhoneOtpInput.class);
        verify(otp).verifyPhoneOtp(phone.capture());
        assertThat(phone.getValue().otp()).isEqualTo("111111");
        assertThat(phone.getValue().totpCode()).isEqualTo("7");
    }

    @Test
    void resetPasswordReturnsMessageAndDelegates() {
        CommonResponseObject out = controller.resetPassword(body(map("email", "r@x.com", "otp", "1", "newPassword", "Longpass#1",
                "userType", "CUSTOMER"))).getBody();

        assertThat(out.getMessage()).isEqualTo("Password reset successful.");
        verify(otp).resetPassword(new UamOtpAuthServiceUseCase.ResetPasswordInput("r@x.com", "1", "Longpass#1", UamEnums.UserType.CUSTOMER));
        assertThatThrownBy(() -> controller.resetPassword(body(map("email", "r@x.com", "otp", "1", "newPassword", "short",
                "userType", "CUSTOMER")))).isInstanceOf(RequestValidationException.class);
    }

    @Test
    void refreshUsesLocalOrProviderPath() {
        when(authService.refresh(any())).thenReturn(new AuthServiceUseCase.RefreshOutput(loginResponse));
        when(router.refresh(any())).thenReturn(new IdentityProviderAuthRouterUseCase.RefreshOutput(loginResponse));

        controller.refresh(body(map("refreshToken", "rt")));
        verify(authService).refresh(new AuthServiceUseCase.RefreshInput("rt"));

        controller.refresh(body(map("refreshToken", "rt2", "providerType", "oidc")));
        verify(router).refresh(new IdentityProviderAuthRouterUseCase.RefreshInput(new ProviderRefreshRequest("oidc", "rt2")));
    }

    @Test
    void aRefusedRefreshIsA401WithTheRefreshTokenInvalidCodeInTheStandardEnvelope() {
        when(authService.refresh(any())).thenThrow(com.heuristiq.uam.refreshToken.RefreshTokenFailures.invalid());

        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(() -> controller.refresh(body(map("refreshToken", "stale"))));
        var response = new com.heuristiq.uam.common.GlobalExceptionHandler().handleUam((com.heuristiq.uam.common.UamException) thrown);

        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().getSuccess()).isFalse();
        assertThat(response.getBody().getErrorCode()).isEqualTo("REFRESH_TOKEN_INVALID");
        assertThat(response.getBody().getMessage()).isEqualTo("Invalid or expired refresh token").doesNotContain("stale");
    }

    @Test
    void logoutRevokesLocalTokensOfAuthenticatedCaller() {
        var auth = new UsernamePasswordAuthenticationToken("user-1", null, List.of());

        CommonResponseObject out = controller.logout(auth, null).getBody();

        assertThat(out.getSuccess()).isTrue();
        verify(refreshTokens).revokeAllForPrincipal(new RefreshTokenServiceUseCase.RevokeAllForPrincipalInput("user-1"));
        verify(router, never()).logout(any());
    }

    @Test
    void logoutWithoutSessionIsAnIdempotentNoOp() {
        assertThat(controller.logout(null, new CommonRequestObject()).getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(refreshTokens, never()).revokeAllForPrincipal(any());
    }

    @Test
    void logoutAlsoRevokesExternalSessionWhenProviderAndTokenGiven() {
        controller.logout(null, body(map("providerType", "keycloak", "refreshToken", "ext")));
        verify(router).logout(new IdentityProviderAuthRouterUseCase.LogoutInput(new ProviderRefreshRequest("keycloak", "ext")));

        controller.logout(null, body(map("providerType", "keycloak")));
        controller.logout(null, body(map("refreshToken", "only")));
        verify(router, org.mockito.Mockito.times(1)).logout(any());
    }
}
