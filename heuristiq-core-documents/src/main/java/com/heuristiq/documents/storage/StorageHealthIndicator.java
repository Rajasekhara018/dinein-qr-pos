package com.railcab.dms.storage;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class StorageHealthIndicator implements HealthIndicator {

    private final StorageProviderRegistry providerRegistry;

    public StorageHealthIndicator(StorageProviderRegistry providerRegistry) {
        this.providerRegistry = providerRegistry;
    }

    @Override
    public Health health() {
        String configured = providerRegistry.currentProviderName();
        try {
            return providerRegistry.current().isHealthy()
                    ? Health.up().withDetail("provider", configured).build()
                    : Health.down().withDetail("provider", configured).build();
        } catch (RuntimeException ex) {
            return Health.down()
                    .withDetail("provider", configured)
                    .withDetail("error", ex.getMessage())
                    .build();
        }
    }
}
