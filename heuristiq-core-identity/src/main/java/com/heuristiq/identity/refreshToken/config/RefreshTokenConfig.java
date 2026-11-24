package com.heuristiq.uam.refreshToken.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Wiring only: binds {@link RefreshTokenProperties}. */
@Configuration
@EnableConfigurationProperties(RefreshTokenProperties.class)
public class RefreshTokenConfig {
}
