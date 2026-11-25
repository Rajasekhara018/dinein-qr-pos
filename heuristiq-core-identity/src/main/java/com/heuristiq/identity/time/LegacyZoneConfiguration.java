package com.heuristiq.identity.time;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Registers {@link LegacyZoneProperties}; wiring only. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(LegacyZoneProperties.class)
public class LegacyZoneConfiguration {
}
