package com.heuristiq.uam.orgstructure.config;

import com.heuristiq.uam.orgstructure.adapter.out.OrgStructureAdapterConstants;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import com.heuristiq.resilient.ResilienceProfile;
import com.heuristiq.resilient.ResilientCaller;

/**
 * Wiring for UAM to Rider_DMS calls: a WebClient that carries the shared gateway token (Rider_DMS rejects any
 * call without it) and a resilient caller with its own timeout, bulkhead and circuit breaker.
 */
@Configuration
@ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "postgres")
@EnableConfigurationProperties(DmsClientProperties.class)
public class DmsClientConfig {

    @Bean
    @Qualifier(OrgStructureAdapterConstants.DMS_WEB_CLIENT_BEAN)
    WebClient orgDmsWebClient(WebClient.Builder builder, DmsClientProperties properties,
                              @Value("${gateway.forward-key:}") String gatewayForwardKey) {
        WebClient.Builder configured = builder.baseUrl(properties.baseUrl())
                // WebClient buffers 256 KB by default, far below a 100k-row employee file.
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(properties.maxFileBytes()));
        if (StringUtils.hasText(gatewayForwardKey)) {
            configured.defaultHeader(OrgStructureAdapterConstants.GATEWAY_TOKEN_HEADER, gatewayForwardKey);
        }
        return configured.build();
    }

    @Bean
    @Qualifier(OrgStructureAdapterConstants.DMS_RESILIENT_CALLER_BEAN)
    ResilientCaller dmsResilientCaller(DmsClientProperties properties) {
        ResilienceProfile profile = ResilienceProfile.defaults()
                .withCallTimeout(Duration.ofMillis(properties.callTimeoutMs()))
                .withMaxAttempts(properties.maxAttempts())
                .withMaxConcurrentCalls(properties.maxConcurrentCalls());
        return new ResilientCaller(OrgStructureAdapterConstants.DMS_SERVICE_NAME, profile);
    }
}
