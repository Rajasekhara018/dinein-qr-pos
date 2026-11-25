package com.heuristiq.notification.infrastructure.cache;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Ticker;
import com.heuristiq.notification.application.port.out.TemplateLookupCache;
import com.heuristiq.notification.domain.model.NotificationChannel;
import com.heuristiq.notification.domain.model.NotificationTemplate;
import com.heuristiq.notification.infrastructure.config.NmsProperties;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * Caffeine-backed {@link TemplateLookupCache} (see docs/CACHING.md). Disabled by {@code nms.cache.enabled=false} or
 * {@code nms.cache.template.enabled=false}, in which case every call runs the loader and returns its result unchanged.
 */
@Component
public class CaffeineTemplateLookupCache implements TemplateLookupCache {

    private static final Logger log = LoggerFactory.getLogger(CaffeineTemplateLookupCache.class);

    /** Null when the cache is disabled. */
    private final OptionalTtlCache<TemplateCacheKey, NotificationTemplate> cache;

    @Autowired
    public CaffeineTemplateLookupCache(NmsProperties properties, ObjectProvider<MeterRegistry> registry) {
        this(properties, Ticker.systemTicker(), registry.getIfAvailable());
    }

    /** For tests: injects the time source and registry. */
    public CaffeineTemplateLookupCache(NmsProperties properties, Ticker ticker, MeterRegistry registry) {
        NmsProperties.Cache cfg = properties.getCache();
        NmsProperties.Spec spec = cfg.getTemplate();
        if (cfg.isActive(spec)) {
            this.cache = new OptionalTtlCache<>(NmsCacheConstants.TEMPLATE_CACHE,
                    Duration.ofSeconds(spec.getTtlSeconds()), Duration.ofSeconds(spec.getNotFoundTtlSeconds()),
                    spec.getMaxSize(), ticker, registry);
            log.info("Template cache enabled: ttl={}s notFoundTtl={}s maxSize={}", spec.getTtlSeconds(),
                    spec.getNotFoundTtlSeconds(), spec.getMaxSize());
        } else {
            this.cache = null;
            log.info("Template cache disabled");
        }
    }

    @Override
    public Optional<NotificationTemplate> resolve(String tenantId, String event, NotificationChannel channel,
                                                  Supplier<Optional<NotificationTemplate>> loader) {
        if (cache == null) {
            return loader.get();
        }
        TemplateCacheKey key = TemplateCacheKey.of(tenantId, event, channel);
        // The stored template is a snapshot taken at load time; every caller gets its own copy of it.
        return cache.get(key, () -> loader.get().map(TemplateSnapshots::copyOf)).map(TemplateSnapshots::copyOf);
    }

    @Override
    public void evictAll() {
        if (cache != null) {
            cache.invalidateAll();
        }
    }

    /** Entries held, for tests. */
    long estimatedSize() {
        return cache == null ? 0 : cache.estimatedSize();
    }
}
