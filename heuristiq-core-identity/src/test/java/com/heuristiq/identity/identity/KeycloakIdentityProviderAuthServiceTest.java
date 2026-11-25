package com.heuristiq.identity.identity;

import com.heuristiq.identity.config.UamProperties;
import com.heuristiq.identity.dto.ExternalUserInfo;
import com.heuristiq.identity.dto.ProviderLoginRequest;
import com.heuristiq.identity.dto.ProviderRefreshRequest;
import com.heuristiq.identity.dto.ProviderTokenResponse;
import com.heuristiq.identity.identity.keycloak.KeycloakIdentityProviderAuthService;
import com.heuristiq.identity.testsupport.StubWebClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KeycloakIdentityProviderAuthServiceTest {
    private static final String TOKEN_JSON = "{\"access_token\":\"at\",\"refresh_token\":\"rt\",\"expires_in\":60}";
    private UamProperties properties;
    private StubWebClient http;
    private KeycloakIdentityProviderAuthService service;

    @BeforeEach
    void setUp() {
        properties = new UamProperties();
        properties.getKeycloak().setServerUrl("http://kc");
        properties.getKeycloak().setRealm("r");
        properties.getKeycloak().setClientId("cid");
        properties.getKeycloak().setClientSecret("sec");
        http = new StubWebClient().respond(HttpStatus.OK, TOKEN_JSON);
        service = new KeycloakIdentityProviderAuthService(properties, http.builder());
    }

    private static ProviderLoginRequest login(String grant, String user, String pw, String totp, String code, String redirect) {
        return new ProviderLoginRequest(null, grant, user, pw, totp, code, redirect, null);
    }

    @Test
    void providerTypeIsKeycloak() {
        assertThat(service.providerType()).isEqualTo(IdentityProviderType.KEYCLOAK);
    }

    @Test
    void passwordLoginPostsFormToTokenEndpointAndParsesTokens() {
        ProviderTokenResponse out = service.login(login(null, "u", "p", "123456", null, null));

        assertThat(out.accessToken()).isEqualTo("at");
        assertThat(out.refreshToken()).isEqualTo("rt");
        assertThat(http.last().url().toString()).isEqualTo("http://kc/realms/r/protocol/openid-connect/token");
        assertThat(http.last().method().name()).isEqualTo("POST");
    }

    @Test
    void publicClientSendsNoSecret() {
        properties.getKeycloak().setClientSecret("");
        assertThat(service.login(new ProviderLoginRequest("u", "p", null)).accessToken()).isEqualTo("at");
    }

    @Test
    void authorizationCodeLoginIsInferredFromCode() {
        assertThat(service.login(login(null, null, null, null, "code", "http://cb")).accessToken()).isEqualTo("at");
    }

    @Test
    void loginValidatesRequiredFieldsPerGrant() {
        assertThatThrownBy(() -> service.login(login("password", " ", "p", null, null, null)))
                .isInstanceOf(ProviderAuthException.class).hasMessageContaining("username");
        assertThatThrownBy(() -> service.login(login("password", "u", null, null, null, null)))
                .isInstanceOf(ProviderAuthException.class).hasMessageContaining("password");
        assertThatThrownBy(() -> service.login(login("authorization_code", null, null, null, null, "r")))
                .isInstanceOf(ProviderAuthException.class).hasMessageContaining("authorizationCode");
        assertThatThrownBy(() -> service.login(login("authorization_code", null, null, null, "c", null)))
                .isInstanceOf(ProviderAuthException.class).hasMessageContaining("redirectUri");
        assertThatThrownBy(() -> service.login(login("implicit", null, null, null, null, null)))
                .isInstanceOfSatisfying(ProviderAuthException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getMessage()).contains("implicit");
                });
        assertThat(http.requests()).isEmpty();
    }

    @Test
    void providerRejectionIsMappedToProviderAuthExceptionWithBody() {
        http.respond(HttpStatus.UNAUTHORIZED, "{\"error\":\"invalid_grant\"}");
        assertThatThrownBy(() -> service.login(new ProviderLoginRequest("u", "p", null)))
                .isInstanceOfSatisfying(ProviderAuthException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(e.getMessage()).contains("invalid_grant");
                });
    }

    @Test
    void providerRejectionWithEmptyBodyUsesGenericMessage() {
        http.respond(HttpStatus.BAD_REQUEST, "");
        assertThatThrownBy(() -> service.refresh(new ProviderRefreshRequest("rt")))
                .isInstanceOfSatisfying(ProviderAuthException.class,
                        e -> assertThat(e.getMessage()).isEqualTo("Identity provider authentication failed."));
    }

    @Test
    void refreshPostsRefreshGrant() {
        assertThat(service.refresh(new ProviderRefreshRequest("old")).accessToken()).isEqualTo("at");
        assertThat(http.last().url().getPath()).endsWith("/token");
    }

    @Test
    void logoutPostsToLogoutEndpointAndMapsFailure() {
        http.respond(HttpStatus.NO_CONTENT, "");
        service.logout(new ProviderRefreshRequest("rt"));
        assertThat(http.last().url().getPath()).endsWith("/logout");

        http.respond(HttpStatus.BAD_REQUEST, "bad");
        assertThatThrownBy(() -> service.logout(new ProviderRefreshRequest("rt")))
                .isInstanceOf(ProviderAuthException.class);
    }

    @Test
    void fetchUserInfoSendsBearerToken() {
        http.respond(HttpStatus.OK, "{\"sub\":\"s\",\"email\":\"e@x.com\",\"preferred_username\":\"pu\",\"extra\":1}");

        ExternalUserInfo info = service.fetchUserInfo("tok");

        assertThat(info.sub()).isEqualTo("s");
        assertThat(info.preferredUsername()).isEqualTo("pu");
        assertThat(http.last().headers().getFirst("Authorization")).isEqualTo("Bearer tok");
        assertThat(http.last().url().getPath()).endsWith("/userinfo");
    }

    @Test
    void fetchUserInfoMapsProviderError() {
        http.respond(HttpStatus.FORBIDDEN, "nope");
        assertThatThrownBy(() -> service.fetchUserInfo("tok"))
                .isInstanceOfSatisfying(ProviderAuthException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void resilienceFallbacksTurnOutagesIntoServiceUnavailableExceptLogout() {
        Throwable down = new IllegalStateException("down");
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "loginFallback",
                login(null, "u", "p", null, null, null), down))
                .isInstanceOfSatisfying(ProviderAuthException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(e.getMessage()).isEqualTo(IdentityProviderConstants.PROVIDER_UNAVAILABLE_MESSAGE);
                });
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "refreshFallback",
                new ProviderRefreshRequest("r"), down)).isInstanceOf(ProviderAuthException.class);
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "fetchUserInfoFallback", "tok", down))
                .isInstanceOf(ProviderAuthException.class);
        // logout is best effort: no exception
        ReflectionTestUtils.invokeMethod(service, "logoutFallback", new ProviderRefreshRequest("r"), down);
    }
}
