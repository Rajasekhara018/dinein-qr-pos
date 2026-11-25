package com.heuristiq.identity.cache;

import com.github.benmanes.caffeine.cache.Ticker;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wiring only: the cache holder (with meters when a registry exists) and the post-processor that hooks the repository ports. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(UamCacheProperties.class)
public class UamCacheConfig {

    @Bean
    UamCaches uamCaches(UamCacheProperties properties, ObjectProvider<MeterRegistry> registry) {
        return UamCaches.create(properties, Ticker.systemTicker(), registry.getIfAvailable());
    }

    /** Static so it is registered early without instantiating this configuration; the caches are resolved lazily. */
    @Bean
    static UamCacheBeanPostProcessor uamCacheBeanPostProcessor(ObjectProvider<UamCaches> caches) {
        return new UamCacheBeanPostProcessor(caches);
    }
}
