package com.heuristiq.uam.identity.keycloak;

import com.heuristiq.uam.identity.IdentityProviderConstants;
import com.heuristiq.uam.config.UamProperties;
import com.heuristiq.uam.dto.ExternalUserInfo;
import com.heuristiq.uam.dto.ProviderLoginRequest;
import com.heuristiq.uam.dto.ProviderRefreshRequest;
import com.heuristiq.uam.dto.ProviderTokenResponse;
import com.heuristiq.uam.identity.IdentityProviderAuthService;
import com.heuristiq.uam.identity.IdentityProviderType;
import com.heuristiq.uam.identity.ProviderAuthException;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;


@Slf4j
@Service
public class KeycloakIdentityProviderAuthService implements IdentityProviderAuthService {

    private final UamProperties properties;
    private final WebClient webClient;

    public KeycloakIdentityProviderAuthService(UamProperties properties, WebClient.Builder webClientBuilder) {
        this.properties = properties;
        this.webClient = webClientBuilder.build();
    }

    @Override
    public IdentityProviderType providerType() {
        return IdentityProviderType.KEYCLOAK;
    }

    @Override
    @Bulkhead(name = IdentityProviderConstants.KEYCLOAK_RESILIENCE)
    @CircuitBreaker(name = IdentityProviderConstants.KEYCLOAK_RESILIENCE, fallbackMethod = "loginFallback")
    @Retry(name = IdentityProviderConstants.KEYCLOAK_RESILIENCE)
    public ProviderTokenResponse login(ProviderLoginRequest request) {
        String grantType = resolveGrantType(request);
        MultiValueMap<String, String> form = baseClientForm();
        form.add(IdentityProviderConstants.PARAM_GRANT_TYPE, grantType);
        if (IdentityProviderConstants.PASSWORD_GRANT.equals(grantType)) {
            requireText(request.username(), "username is required for password grant.");
            requireText(request.password(), "password is required for password grant.");
            form.add(IdentityProviderConstants.PARAM_USERNAME, request.username());
            form.add(IdentityProviderConstants.PARAM_PASSWORD, request.password());
        } else if (IdentityProviderConstants.AUTHORIZATION_CODE_GRANT.equals(grantType)) {
            requireText(request.authorizationCode(), "authorizationCode is required for authorization_code grant.");
            requireText(request.redirectUri(), "redirectUri is required for authorization_code grant.");
            form.add(IdentityProviderConstants.PARAM_CODE, request.authorizationCode());
            form.add(IdentityProviderConstants.PARAM_REDIRECT_URI, request.redirectUri());
        } else {
            throw new ProviderAuthException(HttpStatus.BAD_REQUEST, "Unsupported Keycloak grant_type: " + grantType);
        }
        if (StringUtils.hasText(request.totp())) {
            form.add(IdentityProviderConstants.PARAM_TOTP, request.totp());
        }
        return token(form);
    }

    @Override
    @Bulkhead(name = IdentityProviderConstants.KEYCLOAK_RESILIENCE)
    @CircuitBreaker(name = IdentityProviderConstants.KEYCLOAK_RESILIENCE, fallbackMethod = "refreshFallback")
    @Retry(name = IdentityProviderConstants.KEYCLOAK_RESILIENCE)
    public ProviderTokenResponse refresh(ProviderRefreshRequest request) {
        MultiValueMap<String, String> form = baseClientForm();
        form.add(IdentityProviderConstants.PARAM_GRANT_TYPE, IdentityProviderConstants.REFRESH_TOKEN_GRANT);
        form.add(IdentityProviderConstants.PARAM_REFRESH_TOKEN, request.refreshToken());
        return token(form);
    }

    @Override
    @Bulkhead(name = IdentityProviderConstants.KEYCLOAK_RESILIENCE)
    @CircuitBreaker(name = IdentityProviderConstants.KEYCLOAK_RESILIENCE, fallbackMethod = "logoutFallback")
    @Retry(name = IdentityProviderConstants.KEYCLOAK_RESILIENCE)
    public void logout(ProviderRefreshRequest request) {
        MultiValueMap<String, String> form = baseClientForm();
        form.add(IdentityProviderConstants.PARAM_REFRESH_TOKEN, request.refreshToken());
        try {
            webClient.post()
                    .uri(logoutUri())
                    .body(BodyInserters.fromFormData(form))
                    .retrieve()
                    .toBodilessEntity()
                    .block(IdentityProviderConstants.CALL_TIMEOUT);
        } catch (WebClientResponseException e) {
            throw new ProviderAuthException(HttpStatus.valueOf(e.getStatusCode().value()), providerError(e));
        }
    }

    private ProviderTokenResponse loginFallback(ProviderLoginRequest request, Throwable t) {
        log.error("KeycloakIdentityProviderAuthService: Keycloak unavailable during login: {}", t.getMessage(), t);
        throw new ProviderAuthException(HttpStatus.SERVICE_UNAVAILABLE, IdentityProviderConstants.PROVIDER_UNAVAILABLE_MESSAGE);
    }

    private ProviderTokenResponse refreshFallback(ProviderRefreshRequest request, Throwable t) {
        log.error("KeycloakIdentityProviderAuthService: Keycloak unavailable during token refresh: {}", t.getMessage(), t);
        throw new ProviderAuthException(HttpStatus.SERVICE_UNAVAILABLE, IdentityProviderConstants.PROVIDER_UNAVAILABLE_MESSAGE);
    }

    /** Best-effort: an unreachable Keycloak must not block the caller's local logout. */
    private void logoutFallback(ProviderRefreshRequest request, Throwable t) {
        log.warn("KeycloakIdentityProviderAuthService: Keycloak logout failed, proceeding locally: {}", t.getMessage());
    }

    private ProviderTokenResponse token(MultiValueMap<String, String> form) {
        try {
            return webClient.post()
                    .uri(tokenUri())
                    .body(BodyInserters.fromFormData(form))
                    .retrieve()
                    .bodyToMono(ProviderTokenResponse.class)
                    .block(IdentityProviderConstants.CALL_TIMEOUT);
        } catch (WebClientResponseException e) {
            throw new ProviderAuthException(HttpStatus.valueOf(e.getStatusCode().value()), providerError(e));
        }
    }

    private MultiValueMap<String, String> baseClientForm() {
        UamProperties.Keycloak keycloak = properties.getKeycloak();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add(IdentityProviderConstants.PARAM_CLIENT_ID, keycloak.getClientId());
        if (StringUtils.hasText(keycloak.getClientSecret())) {
            form.add(IdentityProviderConstants.PARAM_CLIENT_SECRET, keycloak.getClientSecret());
        }
        return form;
    }

    private String tokenUri() {
        UamProperties.Keycloak keycloak = properties.getKeycloak();
        return keycloak.getServerUrl() + KeycloakConstants.REALMS_PATH + keycloak.getRealm() + KeycloakConstants.TOKEN_PATH;
    }

    private String logoutUri() {
        UamProperties.Keycloak keycloak = properties.getKeycloak();
        return keycloak.getServerUrl() + KeycloakConstants.REALMS_PATH + keycloak.getRealm() + KeycloakConstants.LOGOUT_PATH;
    }

    @Override
    @Bulkhead(name = IdentityProviderConstants.KEYCLOAK_RESILIENCE)
    @CircuitBreaker(name = IdentityProviderConstants.KEYCLOAK_RESILIENCE, fallbackMethod = "fetchUserInfoFallback")
    @Retry(name = IdentityProviderConstants.KEYCLOAK_RESILIENCE)
    public ExternalUserInfo fetchUserInfo(String accessToken) {
        try {
            return webClient.get()
                    .uri(userInfoUri())
                    .headers(h -> h.setBearerAuth(accessToken))
                    .retrieve()
                    .bodyToMono(ExternalUserInfo.class)
                    .block(IdentityProviderConstants.CALL_TIMEOUT);
        } catch (WebClientResponseException e) {
            throw new ProviderAuthException(HttpStatus.valueOf(e.getStatusCode().value()), providerError(e));
        }
    }

    private ExternalUserInfo fetchUserInfoFallback(String accessToken, Throwable t) {
        log.error("KeycloakIdentityProviderAuthService: Keycloak unavailable fetching user info: {}", t.getMessage(), t);
        throw new ProviderAuthException(HttpStatus.SERVICE_UNAVAILABLE, IdentityProviderConstants.PROVIDER_UNAVAILABLE_MESSAGE);
    }

    private String userInfoUri() {
        UamProperties.Keycloak keycloak = properties.getKeycloak();
        return keycloak.getServerUrl() + KeycloakConstants.REALMS_PATH + keycloak.getRealm() + KeycloakConstants.USERINFO_PATH;
    }

    private String providerError(WebClientResponseException e) {
        return StringUtils.hasText(e.getResponseBodyAsString())
                ? e.getResponseBodyAsString()
                : "Identity provider authentication failed.";
    }

    private String resolveGrantType(ProviderLoginRequest request) {
        if (StringUtils.hasText(request.grantType())) {
            return request.grantType();
        }
        return StringUtils.hasText(request.authorizationCode()) ? IdentityProviderConstants.AUTHORIZATION_CODE_GRANT : IdentityProviderConstants.PASSWORD_GRANT;
    }

    private void requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new ProviderAuthException(HttpStatus.BAD_REQUEST, message);
        }
    }
}
