package com.heuristiq.payment.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(String secret, long expiryDays) {

    public JwtProperties {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("jwt.secret must be configured");
        }
        if (secret.length() < 32) {
            throw new IllegalStateException("jwt.secret must be at least 32 characters for HS256");
        }
        if (expiryDays <= 0) {
            expiryDays = 30;
        }
    }
}
