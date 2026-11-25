package com.heuristiq.notification.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Inbound credential of NMS's service-to-service endpoints ({@code nms.internal-auth.*}).
 *
 * @param serviceToken shared platform service token expected in {@code X-Rider-Service-Token}
 *                     ({@code RIDER_INTERNAL_SERVICE_TOKEN}); blank disables that credential, leaving only a SERVICE JWT
 */
@ConfigurationProperties(prefix = "nms.internal-auth")
public record InternalServiceAuthProperties(@DefaultValue("") String serviceToken) {
}
