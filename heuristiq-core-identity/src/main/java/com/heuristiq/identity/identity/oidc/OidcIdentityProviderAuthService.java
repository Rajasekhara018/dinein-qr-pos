package com.heuristiq.identity.identity.oidc;

import com.heuristiq.identity.identity.IdentityProviderConstants;
import com.heuristiq.identity.config.UamProperties;
import com.heuristiq.identity.dto.ExternalUserInfo;
import com.heuristiq.identity.dto.ProviderLoginRequest;
import com.heuristiq.identity.dto.ProviderRefreshRequest;
import com.heuristiq.identity.dto.ProviderTokenResponse;
import com.heuristiq.identity.identity.IdentityProviderAuthService;
import com.heuristiq.identity.identity.IdentityProviderType;
import com.heuristiq.identity.identity.ProviderAuthException;
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
public class OidcIdentityProviderAuthService implements IdentityProviderAuthService {

    private final UamProperties properties;
    private final WebClient webClient;

    public OidcIdentityProviderAuthService(UamProperties properties, WebClient.Builder webClientBuilder) {
        this.properties = properties;
        this.webClient = webClientBuilder.build();
    }

    @Override
    public IdentityProviderType providerType() {
        return IdentityProviderType.OIDC;
    }

    @Override
    @Bulkhead(name = IdentityProviderConstants.OIDC_RESILIENCE)
    @CircuitBreaker(name = IdentityProviderConstants.OIDC_RESILIENCE, fallbackMethod = "loginFallback")
    @Retry(name = IdentityProviderConstants.OIDC_RESILIENCE)
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
            throw new ProviderAuthException(HttpStatus.BAD_REQUEST, "Unsupported OIDC grant_type: " + grantType);
        }
        if (StringUtils.hasText(request.totp())) {
            form.add(IdentityProviderConstants.PARAM_TOTP, request.totp());
        }
        return token(form);
    }

    @Override
    @Bulkhead(name = IdentityProviderConstants.OIDC_RESILIENCE)
    @CircuitBreaker(name = IdentityProviderConstants.OIDC_RESILIENCE, fallbackMethod = "refreshFallback")
    @Retry(name = IdentityProviderConstants.OIDC_RESILIENCE)
    public ProviderTokenResponse refresh(ProviderRefreshRequest request) {
        MultiValueMap<String, String> form = baseClientForm();
        form.add(IdentityProviderConstants.PARAM_GRANT_TYPE, IdentityProviderConstants.REFRESH_TOKEN_GRANT);
        form.add(IdentityProviderConstants.PARAM_REFRESH_TOKEN, request.refreshToken());
        return token(form);
    }

    @Override
    @Bulkhead(name = IdentityProviderConstants.OIDC_RESILIENCE)
    @CircuitBreaker(name = IdentityProviderConstants.OIDC_RESILIENCE, fallbackMethod = "logoutFallback")
    @Retry(name = IdentityProviderConstants.OIDC_RESILIENCE)
    public void logout(ProviderRefreshRequest request) {
        UamProperties.Oidc oidc = properties.getOidc();
        String endpoint = StringUtils.hasText(oidc.getRevocationUri())
                ? oidc.getRevocationUri()
                : oidc.getLogoutUri();
        if (!StringUtils.hasText(endpoint)) {
            return;
        }
        MultiValueMap<String, String> form = baseClientForm();
        form.add(IdentityProviderConstants.PARAM_REFRESH_TOKEN, request.refreshToken());
        form.add(IdentityProviderConstants.PARAM_TOKEN, request.refreshToken());
        try {
            webClient.post()
                    .uri(endpoint)
                    .body(BodyInserters.fromFormData(form))
                    .retrieve()
                    .toBodilessEntity()
                    .block(IdentityProviderConstants.CALL_TIMEOUT);
        } catch (WebClientResponseException e) {
            throw new ProviderAuthException(HttpStatus.valueOf(e.getStatusCode().value()), providerError(e));
        }
    }

    private ProviderTokenResponse loginFallback(ProviderLoginRequest request, Throwable t) {
        log.error("OidcIdentityProviderAuthService: OIDC provider unavailable during login: {}", t.getMessage(), t);
        throw new ProviderAuthException(HttpStatus.SERVICE_UNAVAILABLE, IdentityProviderConstants.PROVIDER_UNAVAILABLE_MESSAGE);
    }

    private ProviderTokenResponse refreshFallback(ProviderRefreshRequest request, Throwable t) {
        log.error("OidcIdentityProviderAuthService: OIDC provider unavailable during token refresh: {}", t.getMessage(), t);
        throw new ProviderAuthException(HttpStatus.SERVICE_UNAVAILABLE, IdentityProviderConstants.PROVIDER_UNAVAILABLE_MESSAGE);
    }

    /** Best-effort: an unreachable OIDC provider must not block the caller's local logout. */
    private void logoutFallback(ProviderRefreshRequest request, Throwable t) {
        log.warn("OidcIdentityProviderAuthService: OIDC logout failed, proceeding locally: {}", t.getMessage());
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
        UamProperties.Oidc oidc = properties.getOidc();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        requireText(oidc.getClientId(), "OIDC client id must be configured.");
        form.add(IdentityProviderConstants.PARAM_CLIENT_ID, oidc.getClientId());
        if (StringUtils.hasText(oidc.getClientSecret())) {
            form.add(IdentityProviderConstants.PARAM_CLIENT_SECRET, oidc.getClientSecret());
        }
        if (StringUtils.hasText(oidc.getScope())) {
            form.add(IdentityProviderConstants.PARAM_SCOPE, oidc.getScope());
        }
        if (StringUtils.hasText(oidc.getAudience())) {
            form.add(IdentityProviderConstants.PARAM_AUDIENCE, oidc.getAudience());
        }
        return form;
    }

    private String tokenUri() {
        UamProperties.Oidc oidc = properties.getOidc();
        requireText(oidc.getTokenUri(), "OIDC token uri must be configured.");
        return oidc.getTokenUri();
    }

    @Override
    @Bulkhead(name = IdentityProviderConstants.OIDC_RESILIENCE)
    @CircuitBreaker(name = IdentityProviderConstants.OIDC_RESILIENCE, fallbackMethod = "fetchUserInfoFallback")
    @Retry(name = IdentityProviderConstants.OIDC_RESILIENCE)
    public ExternalUserInfo fetchUserInfo(String accessToken) {
        UamProperties.Oidc oidc = properties.getOidc();
        requireText(oidc.getUserInfoUri(), "OIDC user-info uri must be configured.");
        try {
            return webClient.get()
                    .uri(oidc.getUserInfoUri())
                    .headers(h -> h.setBearerAuth(accessToken))
                    .retrieve()
                    .bodyToMono(ExternalUserInfo.class)
                    .block(IdentityProviderConstants.CALL_TIMEOUT);
        } catch (WebClientResponseException e) {
            throw new ProviderAuthException(HttpStatus.valueOf(e.getStatusCode().value()), providerError(e));
        }
    }

    private ExternalUserInfo fetchUserInfoFallback(String accessToken, Throwable t) {
        log.error("OidcIdentityProviderAuthService: OIDC provider unavailable fetching user info: {}", t.getMessage(), t);
        throw new ProviderAuthException(HttpStatus.SERVICE_UNAVAILABLE, IdentityProviderConstants.PROVIDER_UNAVAILABLE_MESSAGE);
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

    private String providerError(WebClientResponseException e) {
        return StringUtils.hasText(e.getResponseBodyAsString())
                ? e.getResponseBodyAsString()
                : "OIDC identity provider authentication failed.";
    }
}
