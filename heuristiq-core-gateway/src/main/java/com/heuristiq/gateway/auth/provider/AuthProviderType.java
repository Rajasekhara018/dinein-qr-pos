package com.heuristiq.gateway.auth.provider;

import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * Identity providers the gateway can validate bearer tokens against.
 *
 * <p>Mirrors UAM's {@code IdentityProviderType} so both services describe identity
 * providers the same way. Selected via {@code railcab.gateway.identity-provider.type}
 * and resolved by {@link AuthProviderRouter} — adding a provider means registering a
 * new {@link IAuthProvider} bean for a new constant here, not adding another mutually
 * exclusive Spring profile.
 */
public enum AuthProviderType {
    HMAC("hmac"),
    KEYCLOAK("keycloak"),
    OIDC("oidc");

    private final String value;

    AuthProviderType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static AuthProviderType from(String value) {
        if (!StringUtils.hasText(value)) {
            return HMAC;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (AuthProviderType type : values()) {
            if (type.value.equals(normalized)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unsupported auth provider type: " + value);
    }
}
