package com.heuristiq.identity.identity;

import com.heuristiq.identity.auth.AuthServiceUseCase;
import com.heuristiq.identity.config.UamProperties;
import com.heuristiq.identity.users.UamUser;
import com.heuristiq.identity.dto.ExternalUserInfo;
import com.heuristiq.identity.dto.LoginResponse;
import com.heuristiq.identity.dto.ProviderLoginRequest;
import com.heuristiq.identity.dto.ProviderRefreshRequest;
import com.heuristiq.identity.dto.ProviderTokenResponse;
import com.heuristiq.identity.enums.UamEnums;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class IdentityProviderAuthRouter implements IdentityProviderAuthRouterUseCase {
    private final UamProperties properties;
    private final Map<IdentityProviderType, IdentityProviderAuthService> providers;
    private final ExternalIdentityResolver externalIdentityResolver;
    private final AuthServiceUseCase authService;

    public IdentityProviderAuthRouter(UamProperties properties, List<IdentityProviderAuthService> providers,
                                      ExternalIdentityResolver externalIdentityResolver, AuthServiceUseCase authService) {
        this.properties = properties;
        this.providers = providers.stream()
                .collect(Collectors.toUnmodifiableMap(
                        IdentityProviderAuthService::providerType,
                        Function.identity()));
        this.externalIdentityResolver = externalIdentityResolver;
        this.authService = authService;
    }

    /**
     * Authenticates against the external provider, then bridges the result onto a local UamUser +
     * UAM-signed JWT (via {@link ExternalIdentityResolver} + {@link AuthServiceUseCase#loginViaExternalProvider})
     * rather than handing the caller the provider's own token -- UAM's own endpoints only ever
     * accept locally-signed JWTs (see JwtAuthenticationFilter), so returning the raw provider token
     * here would leave the caller unable to call anything else in this service.
     */
    public LoginOutput login(LoginInput input) {
        ProviderLoginRequest request = input.request();
        IdentityProviderAuthService provider = resolve(request.providerType());
        ProviderTokenResponse providerToken = provider.login(request);
        return new LoginOutput(bridgeToLocalSession(provider, providerToken, request.userType(), request.totp()));
    }

    public RefreshOutput refresh(RefreshInput input) {
        ProviderRefreshRequest request = input.request();
        IdentityProviderAuthService provider = resolve(request.providerType());
        ProviderTokenResponse providerToken = provider.refresh(request);
        return new RefreshOutput(bridgeToLocalSession(provider, providerToken, null, request.totp()));
    }

    public LogoutOutput logout(LogoutInput input) {
        resolve(input.request().providerType()).logout(input.request());
        return new LogoutOutput();
    }

    private LoginResponse bridgeToLocalSession(IdentityProviderAuthService provider,
                                               ProviderTokenResponse providerToken,
                                               UamEnums.UserType userType, String totp) {
        ExternalUserInfo info = provider.fetchUserInfo(providerToken.accessToken());
        UamUser user = externalIdentityResolver.resolveOrCreate(info, userType);
        return authService.loginViaExternalProvider(new AuthServiceUseCase.LoginViaExternalProviderInput(user, totp)).response();
    }

    private IdentityProviderAuthService resolve(String requestedProvider) {
        String providerValue = properties.getIdentityProvider().isEnabled()
                ? requestedProviderOrDefault(requestedProvider)
                : IdentityProviderType.NONE.value();
        IdentityProviderType providerType = IdentityProviderType.from(providerValue);
        IdentityProviderAuthService provider = providers.get(providerType);
        if (provider == null) {
            throw new ProviderAuthException(
                    HttpStatus.BAD_REQUEST,
                    "Unsupported identity provider: " + providerValue);
        }
        return provider;
    }

    private String requestedProviderOrDefault(String requestedProvider) {
        return StringUtils.hasText(requestedProvider)
                ? requestedProvider
                : properties.getIdentityProvider().getType();
    }
}
