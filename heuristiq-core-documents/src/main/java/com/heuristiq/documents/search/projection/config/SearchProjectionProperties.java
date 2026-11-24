package com.railcab.dms.search.projection.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * How DMS reaches Rider_SearchAPI's projection ingress ({@code railcab.dms.search.projection.*}).
 *
 * @param baseUrl      explicit Search API address; when blank the Eureka name in {@code discoveryUrl} is used
 * @param discoveryUrl Eureka-resolved address of Rider_SearchAPI
 * @param serviceToken the platform's shared service token, sent as X-Rider-Service-Token
 *                     ({@code RIDER_INTERNAL_SERVICE_TOKEN}); while blank a run reports FAILED and sends nothing
 * @param timeoutMs    timeout of one ingress call
 */
@Validated
@ConfigurationProperties(prefix = "railcab.dms.search.projection")
public record SearchProjectionProperties(
        @DefaultValue("") String baseUrl,
        @NotBlank @DefaultValue("http://RealBoom-Search-API") String discoveryUrl,
        @DefaultValue("") String serviceToken,
        @Min(500) @DefaultValue("10000") long timeoutMs
) {
}
