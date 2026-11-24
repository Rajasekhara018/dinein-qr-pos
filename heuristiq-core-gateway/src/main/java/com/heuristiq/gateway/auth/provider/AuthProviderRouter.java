package com.heuristiq.gateway.auth.provider;

import com.heuristiq.gateway.config.GatewayProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Resolves the active {@link IAuthProvider} from every registered provider bean, keyed by
 * {@link AuthProviderType}. Mirrors UAM's {@code IdentityProviderAuthRouter}: every provider
 * implementation is always registered as a bean (no {@code @Profile} gating), and the active
 * one is picked by {@code railcab.gateway.identity-provider.type} instead of by which Spring
 * profile happens to be active. Adding a new IdP is register-a-bean, not add-another-profile.
 */
@Slf4j
@Service
public class AuthProviderRouter {

    private final Map<AuthProviderType, IAuthProvider> providers;
    private final IAuthProvider active;
    private final IAuthProvider hmac;

    public AuthProviderRouter(GatewayProperties properties, List<IAuthProvider> providers) {
        this.providers = providers.stream()
                .collect(Collectors.toUnmodifiableMap(
                        IAuthProvider::providerType,
                        Function.identity()));

        AuthProviderType configured = AuthProviderType.from(properties.getIdentityProvider().getType());
        this.active = this.providers.get(configured);
        if (this.active == null) {
            throw new IllegalStateException(
                    "No IAuthProvider registered for railcab.gateway.identity-provider.type='"
                            + configured.value() + "' (registered: " + this.providers.keySet() + ")");
        }
        this.hmac = this.providers.get(AuthProviderType.HMAC);
        log.info("Auth provider router ready: active={} registered={}", configured, this.providers.keySet());
    }

    /** Returns the provider selected by {@code railcab.gateway.identity-provider.type}. */
    public IAuthProvider resolve() {
        return active;
    }

    /**
     * IDP-agnostic resolution for one specific token: UAM now mints every login (local password,
     * OTP, Google, and Keycloak/OIDC-bridged) as the exact same HMAC-signed JWT -- see UAM's
     * {@code AuthService.loginViaExternalProvider}/{@code IdentityProviderAuthRouter} -- so no
     * token a well-behaved client presents should ever actually be a raw external-IdP token.
     * HMAC is tried first regardless of {@code railcab.gateway.identity-provider.type}, so the
     * gateway keeps validating every real UAM-issued token correctly even if that property is
     * set to keycloak/oidc. Falls back to the statically-configured provider only when the token
     * doesn't verify as HMAC at all (e.g. a raw external-IdP token presented directly to the
     * gateway, bypassing UAM entirely -- the one scenario keycloak/oidc mode still exists for).
     */
    public IAuthProvider resolveForToken(String token) {
        if (hmac != null && hmac != active && hmac.isTokenValid(token)) {
            return hmac;
        }
        return active;
    }

    /**
     * Checks one token with the same provider choice as {@link #resolveForToken(String)}, but verifies it only once:
     * HMAC first (when it is not already the configured provider), and its result is used when it accepts the token;
     * otherwise the configured provider decides.
     */
    public TokenVerification verify(String token) {
        if (hmac != null && hmac != active) {
            TokenVerification viaHmac = hmac.verify(token);
            if (viaHmac.valid()) {
                return viaHmac;
            }
        }
        return active.verify(token);
    }
}
