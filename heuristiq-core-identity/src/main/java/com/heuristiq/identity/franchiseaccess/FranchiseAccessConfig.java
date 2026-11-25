package com.heuristiq.identity.franchiseaccess;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Registers {@link FranchiseAccessProperties}; wiring only. */
@Configuration
@ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "postgres")
@EnableConfigurationProperties(FranchiseAccessProperties.class)
class FranchiseAccessConfig {
}
