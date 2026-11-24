package com.heuristq.dinein.shared.security.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(
        @DefaultValue("true") boolean enabled,
        Rule login,
        Rule order,
        Rule upload) {

    public record Rule(int limit, int windowSeconds) {
    }
}
