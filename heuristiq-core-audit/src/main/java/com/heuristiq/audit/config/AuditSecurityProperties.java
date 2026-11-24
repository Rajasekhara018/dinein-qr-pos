package com.railcab.audit.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for validating the bearer tokens issued by Rider_UAM (and the SERVICE tokens minted by
 * Rider_WorkflowService, which share the same secret).
 *
 * <p>{@code jwtSecret} must equal UAM's {@code railcab.uam.auth.jwt-secret}. There is no default on
 * purpose: the service must fail to start when it is missing (see {@code application.properties}).
 *
 * @param jwtSecret HMAC secret shared with Rider_UAM (min 32 bytes)
 */
@ConfigurationProperties(prefix = "railcab.audit.auth")
public record AuditSecurityProperties(String jwtSecret) {
}
