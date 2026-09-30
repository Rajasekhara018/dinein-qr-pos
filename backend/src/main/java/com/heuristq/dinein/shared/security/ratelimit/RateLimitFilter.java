package com.heuristq.dinein.shared.security.ratelimit;

import com.heuristq.dinein.shared.security.CookieFactory;
import com.heuristq.dinein.shared.security.CurrentStaff;
import com.heuristq.dinein.shared.security.JsonSecurityErrorHandlers;
import com.heuristq.dinein.shared.util.SecureTokens;
import com.heuristq.dinein.shared.web.ApiPaths;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;

/**
 * Applies the configured limits: login 5/min/IP, order placement 10/min/guest session, image upload 30/min/user.
 * Registered in the security chain after authentication so the upload limit can key on the staff user.
 */
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;
    private final RateLimitProperties properties;
    private final JsonSecurityErrorHandlers errorHandlers;

    public RateLimitFilter(RateLimitService rateLimitService, RateLimitProperties properties,
                           JsonSecurityErrorHandlers errorHandlers) {
        this.rateLimitService = rateLimitService;
        this.properties = properties;
        this.errorHandlers = errorHandlers;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if ("POST".equals(request.getMethod())) {
            String path = request.getRequestURI();
            RateLimitProperties.Rule rule = null;
            String key = null;
            if (path.equals(ApiPaths.V1 + "/auth/login") || path.equals(ApiPaths.V1 + "/auth/kitchen-device")) {
                rule = properties.login();
                key = "login:" + request.getRemoteAddr();
            } else if (path.equals(ApiPaths.V1 + "/public/orders")) {
                rule = properties.order();
                key = "order:" + guestKey(request);
            } else if (path.equals(ApiPaths.V1 + "/admin/images")) {
                rule = properties.upload();
                key = "upload:" + CurrentStaff.find().map(p -> "u" + p.userId()).orElse(request.getRemoteAddr());
            } else if (path.equals(ApiPaths.V1 + "/kiosk/pair")) {
                // A 6-digit code is guessable in bulk, so pairing attempts share the strict login limit.
                rule = properties.login();
                key = "kioskpair:" + request.getRemoteAddr();
            } else if (path.equals(ApiPaths.V1 + "/kiosk/staff-unlock")) {
                // A PIN is short, so unlock attempts get the strict login limit on top of the account lockout.
                rule = properties.login();
                key = "kioskunlock:" + request.getRemoteAddr();
            } else if (path.equals(ApiPaths.V1 + "/platform/restaurants")) {
                // Reuses the login rule: same shape of risk (a secret guessed by brute force), no dedicated config.
                rule = properties.login();
                key = "platform:" + request.getRemoteAddr();
            }
            if (rule != null) {
                RateLimitService.Decision decision = rateLimitService.allow(key, rule);
                if (!decision.allowed()) {
                    log.warn("rate_limit.rejected path={} key={}", path, key.substring(0, key.indexOf(':')));
                    response.setHeader("Retry-After", String.valueOf(decision.retryAfterSeconds()));
                    errorHandlers.write(response, HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED",
                            "Too many requests. Please wait a moment and try again.");
                    return;
                }
            }
        }
        chain.doFilter(request, response);
    }

    private String guestKey(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            return Arrays.stream(cookies)
                    .filter(c -> CookieFactory.GUEST_COOKIE.equals(c.getName()))
                    .findFirst()
                    .map(c -> "g" + SecureTokens.sha256Hex(c.getValue()).substring(0, 24))
                    .orElse(request.getRemoteAddr());
        }
        return request.getRemoteAddr();
    }
}
