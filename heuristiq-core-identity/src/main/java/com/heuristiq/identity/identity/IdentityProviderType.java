package com.heuristiq.identity.identity;

import org.springframework.util.StringUtils;

import java.util.Locale;

public enum IdentityProviderType {
    NONE("none"),
    KEYCLOAK("keycloak"),
    OIDC("oidc"),
    OKTA("okta");

    private final String value;

    IdentityProviderType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static IdentityProviderType from(String value) {
        if (!StringUtils.hasText(value)) {
            return NONE;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (IdentityProviderType type : values()) {
            if (type.value.equals(normalized)) {
                return type;
            }
        }
        throw new ProviderAuthException(
                org.springframework.http.HttpStatus.BAD_REQUEST,
                "Unsupported identity provider: " + value);
    }
}
