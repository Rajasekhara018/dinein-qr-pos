package com.heuristiq.payment.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.ride-service")
@Getter
@Setter
public class RideServiceProperties {
    /** Base URL of the Ride microservice, e.g. http://localhost:9115 (no /api/v1 suffix). */
    private String baseUrl = "http://localhost:9115";
}
