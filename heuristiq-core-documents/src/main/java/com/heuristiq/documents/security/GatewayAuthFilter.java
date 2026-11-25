package com.heuristiq.documents.security;

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

@Component
public class GatewayAuthFilter extends OncePerRequestFilter {

    private final String forwardKey;

    public GatewayAuthFilter(@Value("${gateway-forward-key}") String forwardKey) {
        this.forwardKey = forwardKey;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Servlet path: unaffected by the gateway's X-Forwarded-Prefix (forward-headers-strategy=framework).
        String path = request.getServletPath();
        // OpenAPI docs / Swagger UI / actuator -- SecurityConfig's own authorizeHttpRequests
        // already permits these, but this filter runs first (addFilterBefore) and hard-rejects
        // with 401 whenever X-Gateway-Token is missing/wrong, before that permitAll list is ever
        // consulted. A browser fetching /v3/api-docs directly (e.g. via the centralized Swagger
        // aggregator) never has that internal header, and Prometheus scraping /actuator/prometheus
        // never will either, so without this exemption both 401 regardless of SecurityConfig's
        // own rules.
        // The service-to-service paths carry no gateway token (Workflow calls DMS directly); InternalServiceAuthFilter
        // authenticates them with a service credential instead.
        return InternalServiceAuthFilter.isInternalPath(path)
                || path.contains(DmsSecurityConstants.ACTUATOR_SEGMENT)
                || path.contains(DmsSecurityConstants.PUBLIC_DOWNLOAD_SEGMENT)
                || path.contains(DmsSecurityConstants.API_DOCS_PATH)
                || path.contains(DmsSecurityConstants.SWAGGER_UI_PATH)
                || path.startsWith(DmsSecurityConstants.JAVADOC_PATH);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String token = request.getHeader(DmsSecurityConstants.GATEWAY_TOKEN_HEADER);
        if (!StringUtils.hasText(token) || !token.equals(forwardKey)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(DmsSecurityConstants.UNAUTHORIZED_BODY);
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
        String userId = request.getHeader(DmsSecurityConstants.USER_ID_HEADER);
        String role = request.getHeader(DmsSecurityConstants.USER_ROLE_HEADER);
        String tenantId = request.getHeader(DmsSecurityConstants.TENANT_ID_HEADER);
        if (!StringUtils.hasText(userId)) {
            userId = DmsSecurityConstants.INTERNAL_SERVICE_USER;
            role = DmsSecurityConstants.INTERNAL_SERVICE_ROLE;
        }

        List<SimpleGrantedAuthority> authorities = StringUtils.hasText(role)
                ? List.of(new SimpleGrantedAuthority(DmsSecurityConstants.ROLE_PREFIX
                        + role.replaceFirst(DmsSecurityConstants.ROLE_PREFIX_REGEX, "")))
                : List.of();
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                new DmsPrincipal(userId, role, StringUtils.hasText(tenantId) ? tenantId.trim() : null),
                null,
                authorities
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
