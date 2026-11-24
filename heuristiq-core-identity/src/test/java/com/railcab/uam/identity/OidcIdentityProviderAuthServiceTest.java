package com.heuristiq.uam.identity;

import com.heuristiq.uam.config.UamProperties;
import com.heuristiq.uam.dto.ProviderLoginRequest;
import com.heuristiq.uam.dto.ProviderRefreshRequest;
import com.heuristiq.uam.identity.oidc.OidcIdentityProviderAuthService;
import com.heuristiq.uam.testsupport.StubWebClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OidcIdentityProviderAuthServiceTest {
    private static final String TOKEN_JSON = "{\"access_token\":\"at\",\"refresh_token\":\"rt\"}";
    private UamProperties properties;
    private StubWebClient http;
    private OidcIdentityProviderAuthService service;

    @BeforeEach
    void setUp() {
        properties = new UamProperties();
        UamProperties.Oidc oidc = properties.getOidc();
        oidc.setClientId("cid");
        oidc.setClientSecret("sec");
        oidc.setTokenUri("http://idp/token");
        oidc.setUserInfoUri("http://idp/userinfo");
        oidc.setLogoutUri("http://idp/logout");
        oidc.setAudience("aud");
        http = new StubWebClient().respond(HttpStatus.OK, TOKEN_JSON);
        service = new OidcIdentityProviderAuthService(properties, http.builder());
    }

    private static ProviderLoginRequest login(String grant, String user, String pw, String totp, String code, String redirect) {
        return new ProviderLoginRequest(null, grant, user, pw, totp, code, redirect, null);
    }

    @Test
    void providerTypeIsOidc() {
        assertThat(service.providerType()).isEqualTo(IdentityProviderType.OIDC);
    }

    @Test
    void passwordAndCodeGrantsHitTokenEndpoint() {
        assertThat(service.login(login(null, "u", "p", "111111", null, null)).accessToken()).isEqualTo("at");
        assertThat(http.last().url().toString()).isEqualTo("http://idp/token");
        assertThat(service.login(login(null, null, null, null, "c", "http://cb")).refreshToken()).isEqualTo("rt");
    }

    @Test
    void loginValidatesInputPerGrant() {
        assertThatThrownBy(() -> service.login(login("password", null, "p", null, null, null)))
                .isInstanceOf(ProviderAuthException.class).hasMessageContaining("username");
        assertThatThrownBy(() -> service.login(login("password", "u", "", null, null, null)))
                .isInstanceOf(ProviderAuthException.class).hasMessageContaining("password");
        assertThatThrownBy(() -> service.login(login("authorization_code", null, null, null, "", "r")))
                .isInstanceOf(ProviderAuthException.class).hasMessageContaining("authorizationCode");
        assertThatThrownBy(() -> service.login(login("authorization_code", null, null, null, "c", " ")))
                .isInstanceOf(ProviderAuthException.class).hasMessageContaining("redirectUri");
        assertThatThrownBy(() -> service.login(login("device", null, null, null, null, null)))
                .isInstanceOf(ProviderAuthException.class).hasMessageContaining("device");
    }

    @Test
    void clientIdAndTokenUriMustBeConfigured() {
        properties.getOidc().setClientId("");
        assertThatThrownBy(() -> service.refresh(new ProviderRefreshRequest("r")))
                .isInstanceOf(ProviderAuthException.class).hasMessageContaining("client id");
        properties.getOidc().setClientId("cid");
        properties.getOidc().setTokenUri("");
        assertThatThrownBy(() -> service.refresh(new ProviderRefreshRequest("r")))
                .isInstanceOf(ProviderAuthException.class).hasMessageContaining("token uri");
    }

    @Test
    void minimalConfigurationOmitsOptionalFormFields() {
        properties.getOidc().setClientSecret("");
        properties.getOidc().setScope("");
        properties.getOidc().setAudience("");
        assertThat(service.refresh(new ProviderRefreshRequest("r")).accessToken()).isEqualTo("at");
    }

    @Test
    void providerErrorsAreMapped() {
        http.respond(HttpStatus.UNAUTHORIZED, "denied");
        assertThatThrownBy(() -> service.refresh(new ProviderRefreshRequest("r")))
                .isInstanceOfSatisfying(ProviderAuthException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(e.getMessage()).isEqualTo("denied");
                });
        http.respond(HttpStatus.BAD_GATEWAY, "");
        assertThatThrownBy(() -> service.refresh(new ProviderRefreshRequest("r")))
                .isInstanceOfSatisfying(ProviderAuthException.class,
                        e -> assertThat(e.getMessage()).isEqualTo("OIDC identity provider authentication failed."));
    }

    @Test
    void logoutPrefersRevocationUriThenLogoutUriThenNoop() {
        http.respond(HttpStatus.OK, "");
        properties.getOidc().setRevocationUri("http://idp/revoke");
        service.logout(new ProviderRefreshRequest("r"));
        assertThat(http.last().url().toString()).isEqualTo("http://idp/revoke");

        properties.getOidc().setRevocationUri("");
        service.logout(new ProviderRefreshRequest("r"));
        assertThat(http.last().url().toString()).isEqualTo("http://idp/logout");

        properties.getOidc().setLogoutUri("");
        int before = http.requests().size();
        service.logout(new ProviderRefreshRequest("r"));
        assertThat(http.requests()).hasSize(before);
    }

    @Test
    void logoutFailureIsMapped() {
        http.respond(HttpStatus.BAD_REQUEST, "bad");
        assertThatThrownBy(() -> service.logout(new ProviderRefreshRequest("r")))
                .isInstanceOf(ProviderAuthException.class);
    }

    @Test
    void fetchUserInfoRequiresUriAndSendsBearer() {
        http.respond(HttpStatus.OK, "{\"sub\":\"s\",\"name\":\"N\"}");
        assertThat(service.fetchUserInfo("tok").name()).isEqualTo("N");
        assertThat(http.last().headers().getFirst("Authorization")).isEqualTo("Bearer tok");

        http.respond(HttpStatus.UNAUTHORIZED, "x");
        assertThatThrownBy(() -> service.fetchUserInfo("tok")).isInstanceOf(ProviderAuthException.class);

        properties.getOidc().setUserInfoUri("");
        assertThatThrownBy(() -> service.fetchUserInfo("tok"))
                .isInstanceOf(ProviderAuthException.class).hasMessageContaining("user-info");
    }

    @Test
    void fallbacksReportUnavailableExceptLogout() {
        Throwable down = new IllegalStateException("down");
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "loginFallback",
                login(null, "u", "p", null, null, null), down))
                .isInstanceOfSatisfying(ProviderAuthException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "refreshFallback",
                new ProviderRefreshRequest("r"), down)).isInstanceOf(ProviderAuthException.class);
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "fetchUserInfoFallback", "t", down))
                .isInstanceOf(ProviderAuthException.class);
        ReflectionTestUtils.invokeMethod(service, "logoutFallback", new ProviderRefreshRequest("r"), down);
    }
}
