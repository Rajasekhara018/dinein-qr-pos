package com.heuristiq.notification.infrastructure.config;

import com.heuristiq.notification.application.usecase.readiness.NmsReadinessService;
import com.heuristiq.notification.domain.repository.ITemplateRepository;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Wiring of the readiness feature and of the internal endpoints' credential: binds {@link ReadinessProperties} and
 * {@link InternalServiceAuthProperties} and builds the evaluator. No logic.
 */
@Configuration
@EnableConfigurationProperties({ReadinessProperties.class, InternalServiceAuthProperties.class})
public class ReadinessConfig {

    @Bean
    public NmsReadinessService nmsReadinessService(ITemplateRepository templates, Clock clock,
                                                   ReadinessProperties properties) {
        return new NmsReadinessService(templates, clock, properties.timeBudget());
    }
}
