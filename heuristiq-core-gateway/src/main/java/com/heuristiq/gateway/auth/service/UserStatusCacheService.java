package com.heuristiq.gateway.auth.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import org.springframework.beans.factory.annotation.Autowired;
import com.heuristiq.gateway.config.GatewayProperties;
import com.heuristiq.gateway.identity.UamUserStatusRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Single point of truth for account-status checks at the gateway.
 *
 * <p>Every role -- admin, customer, driver, anything else UAM has a UserType for -- resolves
 * through the same {@link UamUserStatusRepository} now, since UAM's {@code uam_users} is the one
 * place account-active status actually lives (replaces the earlier per-role AdminUserRepository/
 * CustomerUserRepository/DriverUserRepository, which pointed at AdminAPI's decommissioned
 * {@code admin_users} collection and, for customers, the wrong BookingsAPI collection entirely).
 *
 * <p>Wraps the repository behind a Caffeine cache so MongoDB/MySQL is only queried once per TTL
 * window per user, regardless of request rate. Active AND inactive results are cached — both are
 * valid outcomes that don't need re-verification on every request within the TTL. The cache key
 * is just {@code userId} now (not role-scoped) since the lookup no longer varies by role.
 *
 * <p>Trade-off: a deactivated account remains accessible for up to
 * {@code railcab.gateway.cache.user-status-ttl-seconds} (default 60 s).
 * This is the standard gateway-level security vs. performance trade-off.
 */
@Slf4j
@Service
public class UserStatusCacheService {

    private final UamUserStatusRepository uamUserStatusRepository;
    private final Cache<String, Boolean> statusCache;

    public UserStatusCacheService(UamUserStatusRepository uamUserStatusRepository, GatewayProperties properties) {
        this.uamUserStatusRepository = uamUserStatusRepository;

        GatewayProperties.Cache cfg = properties.getCache();
        this.statusCache = Caffeine.newBuilder()
                .expireAfterWrite(cfg.getUserStatusTtlSeconds(), TimeUnit.SECONDS)
                .maximumSize(cfg.getUserStatusMaxSize())
                .recordStats()
                .build();

        log.info("UserStatusCacheService ready: ttl={}s maxSize={}",
                cfg.getUserStatusTtlSeconds(), cfg.getUserStatusMaxSize());
    }

    /**
     * Returns {@code true} if the UamUser for {@code userId} exists and is ACTIVE. {@code role}
     * is accepted (and logged) purely for call-site compatibility/diagnostics -- it no longer
     * affects which repository is queried, since every role now lives in the same UAM table.
     */
    public boolean isActive(String userId, String role) {
        // get(key, loader) runs the loader once per key even when many requests of the same user miss at the
        // same moment (a burst after a restart or an expiry), so the database sees one lookup, not one per request.
        boolean active = statusCache.get(userId, uamUserStatusRepository::isActive);
        log.debug("Status check: userId={} role={} active={}", userId, role, active);
        return active;
    }

    /**
     * Publishes the cache's size, hit/miss counts, load time and evictions (meters named
     * {@code cache.*} with {@code cache=gateway.user-status}) so the hit ratio can be watched and the size tuned.
     * Optional: without a registry (unit tests) the cache works the same.
     */
    @Autowired(required = false)
    void bindMetrics(MeterRegistry registry) {
        if (registry != null) {
            CaffeineCacheMetrics.monitor(registry, statusCache, "gateway.user-status");
        }
    }

    /**
     * Immediately removes the cached status for this user so the next request
     * triggers a fresh DB lookup. Call this when account state changes
     * (deactivation, suspension, unlock) are detected by a webhook or admin action.
     */
    public void evict(String userId) {
        statusCache.invalidate(userId);
        log.debug("Cache evicted: userId={}", userId);
    }

    /** Evicts all cached entries — use sparingly (e.g., bulk deactivation events). */
    public void evictAll() {
        statusCache.invalidateAll();
        log.warn("UserStatusCache fully invalidated");
    }
}
