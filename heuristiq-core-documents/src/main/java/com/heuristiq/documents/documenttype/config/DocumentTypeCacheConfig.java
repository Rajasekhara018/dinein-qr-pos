package com.railcab.dms.documenttype.config;

import com.github.benmanes.caffeine.cache.Ticker;
import com.railcab.dms.common.cache.DmsCacheProperties;
import com.railcab.dms.documenttype.adapter.out.cache.CachingDocumentTypeRules;
import com.railcab.dms.documenttype.application.DocumentTypeQueryService;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/** Wiring of the document-type rule cache in front of the database-backed rule lookup. Wiring only. */
@Configuration
@EnableConfigurationProperties(DmsCacheProperties.class)
public class DocumentTypeCacheConfig {

    /**
     * The rule lookup every upload uses: the cache decorator over {@link DocumentTypeQueryService}. It is also the
     * invalidator the apply service calls.
     */
    @Bean
    @Primary
    public CachingDocumentTypeRules cachingDocumentTypeRules(DocumentTypeQueryService queryService,
                                                             DmsCacheProperties properties,
                                                             ObjectProvider<MeterRegistry> registry) {
        return new CachingDocumentTypeRules(queryService, properties, Ticker.systemTicker(), registry.getIfAvailable());
    }
}
