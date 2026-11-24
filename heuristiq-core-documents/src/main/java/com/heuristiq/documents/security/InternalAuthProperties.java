package com.railcab.dms.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Credentials accepted on the service-to-service paths ({@code /api/v1/dms/internal/**}) of this service
 * ({@code railcab.dms.internal.*}). Both are optional; an empty value disables that credential, and with both empty
 * the internal paths answer 401 to everyone.
 *
 * @param serviceToken value of the {@code X-Rider-Service-Token} header (the platform's shared service token)
 * @param jwtSecret    HMAC secret of the platform's JWTs; Rider_WorkflowService signs a role-SERVICE token with it for
 *                     the apply callback (at least 32 bytes)
 */
@ConfigurationProperties(prefix = "railcab.dms.internal")
public record InternalAuthProperties(
        @DefaultValue("") String serviceToken,
        @DefaultValue("") String jwtSecret
) {
}
