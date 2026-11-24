package com.heuristiq.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.PropertiesPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The gateway's {@code prod} profile must not force the service registry off: a deployment with a registry (the Docker one) routes
 * through {@code lb://REALBOOM-*}, and a profile that switched the client off made every call fail with "No servers available".
 * Only {@code GATEWAY_DISCOVERY_ENABLED=false} (Kubernetes) turns it off.
 */
class ProdProfileDiscoveryTest {

    private PropertySource<?> load(String name) throws IOException {
        List<PropertySource<?>> sources = new PropertiesPropertySourceLoader().load(name, new ClassPathResource(name));
        return sources.get(0);
    }

    @Test
    void theProdProfileDoesNotSwitchTheRegistryClientOff() throws IOException {
        assertThat(load("application-prod.properties").getProperty("eureka.client.enabled")).isNull();
    }

    @Test
    void theRegistryClientFollowsTheDiscoverySwitchInTheBaseConfiguration() throws IOException {
        assertThat(load("application.properties").getProperty("eureka.client.enabled"))
                .isEqualTo("${railcab.gateway.discovery.enabled}");
        assertThat(load("application.properties").getProperty("railcab.gateway.discovery.enabled"))
                .isEqualTo("${GATEWAY_DISCOVERY_ENABLED:true}");
    }
}
