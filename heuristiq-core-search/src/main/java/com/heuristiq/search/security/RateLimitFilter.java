package com.heuristiq.search.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Redis-backed request rate limiter (fixed window). The counter and its expiry are set by one atomic
 * Lua script, so a crash between the two can no longer leave a counter that never expires. Search had
 * none at all previously, unlike every other backend service. Single "api" bucket (no separate auth bucket -- search has no login endpoint of its
 * own to distinguish).
 */
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final StringRedisTemplate redisTemplate;
    private final RateLimitProperties properties;
    @Value("${rider.internal.service-token:${RIDER_INTERNAL_SERVICE_TOKEN:}}")
    private String projectionServiceToken;

    /** INCR and EXPIRE in one round trip, atomically (see {@link SearchSecurityConstants#RATE_LIMIT_SCRIPT}). */
    private static final RedisScript<Long> RATE_LIMIT = RedisScript.of(SearchSecurityConstants.RATE_LIMIT_SCRIPT, Long.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean trustedProjection = "POST".equals(request.getMethod())
                && "/api/v1/search/internal/projections".equals(request.getServletPath())
                && projectionServiceToken != null && !projectionServiceToken.isBlank()
                && request.getHeader("X-Rider-Service-Token") != null
                && java.security.MessageDigest.isEqual(projectionServiceToken.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        request.getHeader("X-Rider-Service-Token").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        // A bulk source scan is authenticated again by GatewayAuthFilter and must not consume browser search quotas.
        if (!properties.isEnabled() || trustedProjection) {
            chain.doFilter(request, response);
            return;
        }

        RateLimitProperties.EndpointLimit limit = properties.getApi();
        String identifier = identifierFor(request);
        String key = SearchSecurityConstants.RATE_LIMIT_KEY_PREFIX + identifier;

        Long count = redisTemplate.execute(RATE_LIMIT, List.of(key), String.valueOf(limit.getWindowSeconds()));

        if (count != null && count > limit.getLimit()) {
            sendTooManyRequests(response);
            return;
        }

        chain.doFilter(request, response);
    }

    private String identifierFor(HttpServletRequest request) {
        String userId = request.getHeader(SearchSecurityConstants.USER_ID_HEADER);
        if (userId != null && !userId.isBlank()) {
            return SearchSecurityConstants.RATE_LIMIT_USER_PREFIX + userId;
        }
        return SearchSecurityConstants.RATE_LIMIT_IP_PREFIX + request.getRemoteAddr();
    }

    private void sendTooManyRequests(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(SearchSecurityConstants.RATE_LIMIT_EXCEEDED_BODY);
    }
}
