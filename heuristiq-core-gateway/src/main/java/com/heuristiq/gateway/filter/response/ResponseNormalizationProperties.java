package com.heuristiq.gateway.filter.response;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * railcab.gateway.response-normalization.enabled — OFF by default. Neither Flutter app currently
 * routes through this gateway (both call each service directly — see their app_config.dart), so
 * enabling this before that changes would normalize a response no client here actually consumes
 * through the gateway yet. Flip this to true (no code change) once mobile traffic is actually
 * routed through Rider_ApiGateway.
 */
@Component
@ConfigurationProperties(prefix = "railcab.gateway.response-normalization")
@Getter
@Setter
public class ResponseNormalizationProperties {
    private boolean enabled = false;
}
