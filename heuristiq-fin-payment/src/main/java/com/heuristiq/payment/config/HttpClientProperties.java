package com.heuristiq.paymentservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Shared connect/read/write/response timeout budget for every outbound WebClient in this
 * service (calls to the Ride service). Fail-fast rather than blocking a payment webhook
 * response indefinitely on a slow downstream call.
 */
@Component
@ConfigurationProperties(prefix = "rider.http-client")
@Getter
@Setter
public class HttpClientProperties {
    private int connectTimeoutMs = 3000;
    private int readTimeoutMs = 5000;
    private int writeTimeoutMs = 5000;
    private int responseTimeoutMs = 5000;
}
