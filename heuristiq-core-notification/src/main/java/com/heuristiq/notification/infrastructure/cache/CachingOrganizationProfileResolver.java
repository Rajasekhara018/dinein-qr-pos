package com.heuristiq.notification.infrastructure.cache;

import java.time.Duration;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Ticker;
import com.heuristiq.notification.application.dto.OrganizationProfile;
import com.heuristiq.notification.application.port.out.OrganizationProfileResolver;
import com.heuristiq.notification.application.port.out.OrganizationProfileSource;
import com.heuristiq.notification.infrastructure.config.NmsProperties;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * The {@link OrganizationProfileResolver} messages use: a per-organization cache in front of the UAM lookup
 * ({@link OrganizationProfileSource}). A found profile is kept {@code nms.cache.organization-profile.ttl-seconds}, a
 * "not found" answer only {@code not-found-ttl-seconds}. A failed lookup is never kept: it is logged and answered with an
 * empty profile (the platform name is used), exactly as before caching, and the next message tries UAM again. The profile
 * has no writer in NMS, so only the TTL refreshes it. Profiles are immutable records, so sharing them is safe.
 */
@Component
public class CachingOrganizationProfileResolver implements OrganizationProfileResolver {

    private static final Logger log = LoggerFactory.getLogger(CachingOrganizationProfileResolver.class);

    private final OrganizationProfileSource source;
    /** Null when the cache is disabled. */
    private final OptionalTtlCache<String, OrganizationProfile> cache;

    @Autowired
    public CachingOrganizationProfileResolver(OrganizationProfileSource source, NmsProperties properties,
                                              ObjectProvider<MeterRegistry> registry) {
        this(source, properties, Ticker.systemTicker(), registry.getIfAvailable());
    }

    /** For tests: injects the time source and registry. */
    public CachingOrganizationProfileResolver(OrganizationProfileSource source, NmsProperties properties, Ticker ticker,
                                              MeterRegistry registry) {
        this.source = source;
        NmsProperties.Cache cfg = properties.getCache();
        NmsProperties.Spec spec = cfg.getOrganizationProfile();
        if (cfg.isActive(spec)) {
            this.cache = new OptionalTtlCache<>(NmsCacheConstants.ORGANIZATION_PROFILE_CACHE,
                    Duration.ofSeconds(spec.getTtlSeconds()), Duration.ofSeconds(spec.getNotFoundTtlSeconds()),
                    spec.getMaxSize(), ticker, registry);
            log.info("Organization profile cache enabled: ttl={}s notFoundTtl={}s maxSize={}", spec.getTtlSeconds(),
                    spec.getNotFoundTtlSeconds(), spec.getMaxSize());
        } else {
            this.cache = null;
            log.info("Organization profile cache disabled");
        }
    }

    @Override
    public Optional<OrganizationProfile> resolve(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return Optional.empty();
        }
        try {
            return cache == null ? source.fetch(tenantId) : cache.get(tenantId, () -> source.fetch(tenantId));
        } catch (RuntimeException e) {
            log.warn("Could not read organization {} from UAM; messages use the platform name: {}", tenantId, e.getMessage());
            return Optional.empty();
        }
    }
}
