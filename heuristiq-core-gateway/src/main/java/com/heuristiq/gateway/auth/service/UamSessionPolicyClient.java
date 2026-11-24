package com.heuristiq.gateway.auth.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import java.time.Duration;

/** No positive-result cache: session revocation and policy changes apply at the next protected request. */
@Component
public class UamSessionPolicyClient {
    private final WebClient client;
    private final ObjectProvider<DiscoveryClient> discovery;
    private final boolean discoveryEnabled;
    private final String staticUrl;

    public UamSessionPolicyClient(WebClient.Builder builder, ObjectProvider<DiscoveryClient> discovery,
            @Value("${railcab.gateway.discovery.enabled:true}") boolean discoveryEnabled,
            @Value("${railcab.gateway.static.uam-api-url:http://localhost:9103}") String staticUrl) {
        this.client = builder.build(); this.discovery = discovery;
        this.discoveryEnabled = discoveryEnabled; this.staticUrl = staticUrl;
    }

    public boolean active(String token) {
        try {
            String origin = staticUrl;
            if (discoveryEnabled) {
                DiscoveryClient registry = discovery.getIfAvailable();
                if (registry == null) return false;
                var instances = registry.getInstances("REALBOOM-UAM");
                if (instances.isEmpty()) return false;
                origin = instances.getFirst().getUri().toString();
            }
            return Boolean.TRUE.equals(client.get().uri(origin + "/api/v1/uam/auth/session-status")
                    .headers(headers -> headers.setBearerAuth(token))
                    .exchangeToMono(response -> response.releaseBody().thenReturn(response.statusCode().is2xxSuccessful()))
                    .timeout(Duration.ofSeconds(3)).block());
        } catch (RuntimeException unavailableOrDenied) {
            return false;
        }
    }
}
