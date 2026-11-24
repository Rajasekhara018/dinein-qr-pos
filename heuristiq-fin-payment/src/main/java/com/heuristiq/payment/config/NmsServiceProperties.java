package com.heuristiq.payment.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Same shape/convention as {@link RideServiceProperties} for this service's other outbound
 * dependency.
 */
@Component
@ConfigurationProperties(prefix = "app.nms-service")
@Getter
@Setter
public class NmsServiceProperties {
    /** Base URL of the Notification microservice, e.g. http://localhost:9111 (NMS mounts /api/v1/nms/notify -- see NmsServiceClient/RestClientConfig). */
    private String baseUrl = "http://localhost:9111";
}
