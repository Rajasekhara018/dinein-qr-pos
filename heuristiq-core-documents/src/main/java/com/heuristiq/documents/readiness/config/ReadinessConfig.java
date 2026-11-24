package com.railcab.dms.readiness.config;

import com.railcab.dms.documenttype.application.DocumentTypeQueryService;
import com.railcab.dms.readiness.application.DmsReadinessService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Wiring of the readiness feature: binds {@link ReadinessProperties} and builds the evaluator. No logic. */
@Configuration
@EnableConfigurationProperties(ReadinessProperties.class)
public class ReadinessConfig {

    @Bean
    public DmsReadinessService dmsReadinessService(DocumentTypeQueryService documentTypes, Clock clock,
                                                   ReadinessProperties properties) {
        return new DmsReadinessService(documentTypes, clock, properties.timeBudget());
    }
}
