package com.heuristiq.search.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {
    private boolean enabled = true;
    private EndpointLimit api = new EndpointLimit(100, 60);

    @Data
    public static class EndpointLimit {
        private int limit;
        private int windowSeconds;

        public EndpointLimit(int limit, int windowSeconds) {
            this.limit = limit;
            this.windowSeconds = windowSeconds;
        }

        public EndpointLimit() {
        }
    }
}
