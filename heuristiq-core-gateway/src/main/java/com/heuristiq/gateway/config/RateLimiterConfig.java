package com.heuristiq.gateway.config;

import com.heuristiq.gateway.common.constants.GatewayConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

/**
 * Redis-backed rate limiting beans for Spring Cloud Gateway's {@code RequestRateLimiter} filter.
 *
 * <p>Only activated when {@code railcab.gateway.rate-limit.enabled=true} (the default).
 * Set to {@code false} in environments without Redis (e.g., local dev without Docker).
 *
 * <p>Limiters:
 * <ul>
 *   <li>{@code apiRateLimiter} — general requests (50 req/s, burst 100), keyed by user, else by client address</li>
 *   <li>{@code authRateLimiter} — the whole auth surface (5 req/s, burst 10), keyed by client address</li>
 *   <li>{@code loginRateLimiter} — password login only, per client address (2 req/s, burst 5)</li>
 *   <li>{@code loginGlobalRateLimiter} — password login only, ONE bucket for everybody (10 req/s, burst 20): sized to what UAM can
 *       hash per second (bcrypt runs on a few CPU threads and gives up after 2 s with a 503), so an overload is refused here with
 *       a clean 429 and never reaches UAM's queue</li>
 *   <li>{@code publicTrackRateLimiter} — public live-trip tracking only, per client address (1 req/s, burst 5)</li>
 *   <li>{@code registerRateLimiter} — public account sign-up only, per client address (20 an hour, burst 10), on top of the
 *       auth surface's limit: sign-up reveals whether a phone or email is registered (409), so it must not be scriptable</li>
 * </ul>
 *
 * <p>The client address is the real caller behind the proxy (see {@link ClientIpResolver}), not the proxy's own address. Every key
 * has its own prefix ({@link RateLimitConstants}) because the Redis limiter keeps a bucket per KEY, not per route.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "railcab.gateway.rate-limit", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RateLimiterConfig {

    private final GatewayProperties properties;

    @Bean
    @Primary
    public RedisRateLimiter apiRateLimiter() {
        int replenish = properties.getRateLimit().getReplenishRate();
        int burst     = properties.getRateLimit().getBurstCapacity();
        log.info("API rate limiter: replenishRate={} burstCapacity={}", replenish, burst);
        return new RedisRateLimiter(replenish, burst);
    }

    @Bean
    public RedisRateLimiter authRateLimiter() {
        int replenish = properties.getRateLimit().getAuthReplenishRate();
        int burst     = properties.getRateLimit().getAuthBurstCapacity();
        log.info("Auth rate limiter: replenishRate={} burstCapacity={}", replenish, burst);
        return new RedisRateLimiter(replenish, burst);
    }

    @Bean
    public RedisRateLimiter loginRateLimiter() {
        int replenish = properties.getRateLimit().getLoginReplenishRate();
        int burst     = properties.getRateLimit().getLoginBurstCapacity();
        log.info("Login rate limiter (per client address): replenishRate={} burstCapacity={}", replenish, burst);
        return new RedisRateLimiter(replenish, burst);
    }

    @Bean
    public RedisRateLimiter loginGlobalRateLimiter() {
        int replenish = properties.getRateLimit().getLoginGlobalReplenishRate();
        int burst     = properties.getRateLimit().getLoginGlobalBurstCapacity();
        log.info("Login rate limiter (all callers together): replenishRate={} burstCapacity={}", replenish, burst);
        return new RedisRateLimiter(replenish, burst);
    }

    @Bean
    public RedisRateLimiter publicTrackRateLimiter() {
        int replenish = properties.getRateLimit().getPublicTrackReplenishRate();
        int burst     = properties.getRateLimit().getPublicTrackBurstCapacity();
        log.info("Public trip-tracking rate limiter (per client address): replenishRate={} burstCapacity={}", replenish, burst);
        return new RedisRateLimiter(replenish, burst);
    }

    @Bean
    public RedisRateLimiter refreshRateLimiter() {
        int replenish = properties.getRateLimit().getRefreshReplenishRate();
        int burst     = properties.getRateLimit().getRefreshBurstCapacity();
        log.info("Token refresh rate limiter (per client address): replenishRate={} burstCapacity={}", replenish, burst);
        return new RedisRateLimiter(replenish, burst);
    }

    /**
     * Public sign-up, per client address, as an hourly budget. The Redis limiter refills per second, so the bucket refills one
     * token a second and each sign-up costs {@code 3600 / registerPerHour} tokens: {@code registerPerHour} sign-ups an hour,
     * with room for {@code registerBurst} back to back.
     */
    @Bean
    public RedisRateLimiter registerRateLimiter() {
        int perHour = properties.getRateLimit().getRegisterPerHour();
        int burst   = properties.getRateLimit().getRegisterBurst();
        if (perHour < 1 || perHour > RateLimitConstants.SECONDS_PER_HOUR || burst < 1) {
            throw new IllegalStateException("railcab.gateway.rate-limit.register-per-hour must be 1-3600 and register-burst at least 1"
                    + " (got " + perHour + " and " + burst + ")");
        }
        int tokensPerSignUp = RateLimitConstants.SECONDS_PER_HOUR / perHour;
        log.info("Sign-up rate limiter (per client address): {} per hour, burst {}", perHour, burst);
        return new RedisRateLimiter(1, burst * tokensPerSignUp, tokensPerSignUp);
    }

    /** Public sign-up: always the client address (the caller is not signed in), in its own bucket. */
    @Bean
    public KeyResolver registerIpKeyResolver() {
        return exchange -> Mono.just(RateLimitConstants.KEY_REGISTER_IP + clientAddress(exchange));
    }

    /**
     * Rate-limit key: authenticated user ID first, then the real client address as fallback.
     * This ensures per-user throttling on authenticated routes and per-address on public ones.
     */
    @Bean
    @Primary
    public KeyResolver userOrIpKeyResolver() {
        return exchange -> {
            String userId = exchange.getRequest().getHeaders().getFirst(GatewayConstants.X_USER_ID);
            if (userId != null && !userId.isBlank()) {
                return Mono.just(RateLimitConstants.KEY_USER + userId);
            }
            return Mono.just(RateLimitConstants.KEY_IP + clientAddress(exchange));
        };
    }

    /** Auth surface: always the client address (the caller is not signed in yet), in its own bucket. */
    @Bean
    public KeyResolver authIpKeyResolver() {
        return exchange -> Mono.just(RateLimitConstants.KEY_AUTH_IP + clientAddress(exchange));
    }

    /** Login: always the client address, in its own bucket. */
    @Bean
    public KeyResolver loginIpKeyResolver() {
        return exchange -> Mono.just(RateLimitConstants.KEY_LOGIN_IP + clientAddress(exchange));
    }

    /**
     * Public trip tracking: always the client address, in its own bucket. Never the user id: the caller is not signed in,
     * and a client-sent X-User-Id (stripped by the JWT filter anyway) must not be able to pick a fresh bucket.
     */
    @Bean
    public KeyResolver publicTrackIpKeyResolver() {
        return exchange -> Mono.just(RateLimitConstants.KEY_PUBLIC_TRACK_IP + clientAddress(exchange));
    }

    /** Token refresh: always the client address (the access token has expired, so there is no trusted user yet), own bucket. */
    @Bean
    public KeyResolver refreshIpKeyResolver() {
        return exchange -> Mono.just(RateLimitConstants.KEY_REFRESH_IP + clientAddress(exchange));
    }

    /** Login, all callers together: one constant key. */
    @Bean
    public KeyResolver loginGlobalKeyResolver() {
        return exchange -> Mono.just(RateLimitConstants.KEY_LOGIN_GLOBAL);
    }

    private String clientAddress(org.springframework.web.server.ServerWebExchange exchange) {
        return ClientIpResolver.resolve(exchange.getRequest(), properties.getRateLimit().getTrustedProxyHops());
    }
}
