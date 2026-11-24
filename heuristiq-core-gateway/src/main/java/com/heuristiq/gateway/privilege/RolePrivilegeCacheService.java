package com.heuristiq.gateway.privilege;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.Ticker;
import com.heuristiq.gateway.config.GatewayProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Single point of truth for "what privilege numbers does this role currently have" at the
 * gateway. Wraps {@link RolePrivilegeRepository} behind a short-TTL Caffeine cache — same
 * trade-off as {@code UserStatusCacheService}: a role's permissions can change up to
 * {@code railcab.gateway.cache.role-privilege-ttl-seconds} (default 30s) before a request
 * reflects it, in exchange for not hitting MongoDB on every single gatewayed request.
 *
 * <p>Loading is single-flight: when many requests miss the same role at once, one of them reads the database and
 * the others wait for that read instead of each running the same query. A role still in use is reloaded in the
 * background once {@link PrivilegeCacheConstants#REFRESH_PERCENT_OF_TTL} percent of the TTL has passed, so a busy
 * role rarely makes a request wait; an entry is never served once it is older than the TTL.
 */
@Slf4j
@Service
public class RolePrivilegeCacheService {

    private final RolePrivilegeRepository repository;
    private final LoadingCache<String, Set<Integer>> cache;

    @Autowired
    public RolePrivilegeCacheService(RolePrivilegeRepository repository, GatewayProperties properties) {
        this(repository, properties, Executors.newVirtualThreadPerTaskExecutor(), Ticker.systemTicker());
    }

    /**
     * Visible for tests: {@code refreshExecutor} runs the background reloads (the database read is blocking) and
     * {@code ticker} is the clock the TTL is measured with.
     */
    RolePrivilegeCacheService(RolePrivilegeRepository repository, GatewayProperties properties, Executor refreshExecutor,
            Ticker ticker) {
        this.repository = repository;

        GatewayProperties.Cache cfg = properties.getCache();
        Duration ttl = Duration.ofSeconds(cfg.getRolePrivilegeTtlSeconds());
        Caffeine<Object, Object> builder = Caffeine.newBuilder()
                .expireAfterWrite(ttl)
                .maximumSize(cfg.getRolePrivilegeMaxSize())
                .executor(refreshExecutor)
                .ticker(ticker);
        Duration refresh = refreshAfter(ttl);
        if (!refresh.isZero()) {
            builder.refreshAfterWrite(refresh);
        }
        this.cache = builder.build(this::load);

        log.info("RolePrivilegeCacheService ready: ttl={}s maxSize={}",
                cfg.getRolePrivilegeTtlSeconds(), cfg.getRolePrivilegeMaxSize());
    }

    /** Returns the current set of privilege numbers granted to {@code roleCode}. */
    public Set<Integer> resolvePrivilegeIds(String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            return Set.of();
        }
        return cache.get(roleCode);
    }

    private Set<Integer> load(String roleCode) {
        Set<Integer> resolved = repository.resolvePrivilegeIds(roleCode);
        log.debug("Cache load -> DB: role={} privileges={}", roleCode, resolved);
        return resolved;
    }

    /** The background-reload delay for a TTL; zero (no background reload) when the TTL is too short to have one. */
    static Duration refreshAfter(Duration ttl) {
        long millis = ttl.toMillis() * PrivilegeCacheConstants.REFRESH_PERCENT_OF_TTL / PrivilegeCacheConstants.PERCENT;
        return millis >= PrivilegeCacheConstants.MIN_REFRESH_MILLIS ? Duration.ofMillis(millis) : Duration.ZERO;
    }

    /** Returns the union of privilege numbers granted across ALL of {@code roleCodes} — a caller
     *  with more than one role gets the privileges of every role they hold, not just one. Each
     *  role is still resolved (and cached) individually via {@link #resolvePrivilegeIds(String)},
     *  so this adds no new cache entries beyond the per-role ones already in use. */
    public Set<Integer> resolvePrivilegeIds(Collection<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return Set.of();
        }
        Set<Integer> union = new HashSet<>();
        for (String roleCode : roleCodes) {
            union.addAll(resolvePrivilegeIds(roleCode));
        }
        return union;
    }

    /** Call when a role's permissions change (e.g. via RoleController) so the next request
     *  reflects it immediately instead of waiting out the TTL. */
    public void evict(String roleCode) {
        cache.invalidate(roleCode);
        log.debug("Cache evicted: role={}", roleCode);
    }

    public void evictAll() {
        cache.invalidateAll();
        log.warn("RolePrivilegeCache fully invalidated");
    }
}
