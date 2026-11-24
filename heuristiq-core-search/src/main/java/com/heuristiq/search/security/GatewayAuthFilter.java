package com.heuristiq.search.security;

import static com.heuristiq.search.security.SearchSecurityConstants.*;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Without this, every search endpoint was reachable by anyone who could reach port 9116 -- no
 * authentication at all, unlike every other backend service in this repo. Mirrors DMS's
 * GatewayAuthFilter: requires the same X-Gateway-Token the ApiGateway injects on every request it
 * forwards, so a direct (non-Gateway-routed) request is rejected before it ever reaches a
 * controller.
 */
@Component
public class GatewayAuthFilter extends OncePerRequestFilter {

    private final String forwardKey;
    @Value("${rider.internal.service-token:${RIDER_INTERNAL_SERVICE_TOKEN:}}")
    private String projectionServiceToken;

    public GatewayAuthFilter(@Value("${gateway.forward-key}") String forwardKey) {
        this.forwardKey = forwardKey;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Servlet path: unaffected by the gateway's X-Forwarded-Prefix (forward-headers-strategy=framework).
        String path = request.getServletPath();
        // Broadened from /actuator/health so Prometheus can scrape /actuator/prometheus without
        // an app-level auth header -- SecurityConfig's own authorizeHttpRequests permits the same
        // path, but this filter runs first (addFilterBefore) and would 401 it before that rule
        // is ever consulted otherwise.
        return path.contains(ACTUATOR_PATH_FRAGMENT)
                || path.contains(API_DOCS_PATH_FRAGMENT)
                || path.contains(SWAGGER_UI_PATH_FRAGMENT)
                || path.startsWith(JAVADOC_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (request.getServletPath().equals("/api/v1/search/internal/projections")) {
            String supplied = request.getHeader("X-Rider-Service-Token");
            if (projectionServiceToken == null || projectionServiceToken.isBlank() || supplied == null
                    || !java.security.MessageDigest.isEqual(projectionServiceToken.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                            supplied.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write(UNAUTHORIZED_BODY);
                return;
            }
            try {
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                        INTERNAL_SERVICE_USER, null, List.of(new SimpleGrantedAuthority(ROLE_PREFIX + INTERNAL_SERVICE_ROLE))));
                filterChain.doFilter(request, response);
            } finally { SecurityContextHolder.clearContext(); }
            return;
        }
        String token = request.getHeader(GATEWAY_TOKEN_HEADER);
        if (!StringUtils.hasText(token) || !token.equals(forwardKey)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(UNAUTHORIZED_BODY);
            return;
        }
        try {
            authenticateGatewayUser(request);
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void authenticateGatewayUser(HttpServletRequest request) {
        String userId = request.getHeader(USER_ID_HEADER);
        String role = request.getHeader(USER_ROLE_HEADER);
        if (!StringUtils.hasText(userId)) {
            userId = INTERNAL_SERVICE_USER;
            role = INTERNAL_SERVICE_ROLE;
        }

        List<SimpleGrantedAuthority> authorities = StringUtils.hasText(role)
                ? List.of(new SimpleGrantedAuthority(ROLE_PREFIX + role.replaceFirst(ROLE_PREFIX_PATTERN, "")))
                : List.of();
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                userId,
                null,
                authorities
        );
        String tenantId = request.getHeader(TENANT_ID_HEADER);
        // Only an organization-wide caller (the gateway sets X-Org-Wide from the verified token) can widen, and only by asking.
        boolean allOrganizations = TRUE_VALUE.equalsIgnoreCase(request.getHeader(ORG_WIDE_HEADER))
                && TRUE_VALUE.equalsIgnoreCase(request.getHeader(VIEW_ALL_ORGANIZATIONS_HEADER));
        authentication.setDetails(new GatewayCaller(StringUtils.hasText(tenantId) ? tenantId.trim() : null, allOrganizations));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
