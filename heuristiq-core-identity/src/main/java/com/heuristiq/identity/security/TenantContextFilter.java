package com.heuristiq.identity.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Resolves the caller's tenant (if any) from whichever authentication mechanism populated
 * {@link SecurityContextHolder} for this request -- the internal JWT filter or the external
 * OAuth2/Keycloak resource-server path -- and exposes it via {@link TenantContext} so services
 * can enforce tenant-scoped access on ID-based lookups.
 */
@Component
public class TenantContextFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            resolveTenantId().ifPresent(TenantContext::set);
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private Optional<String> resolveTenantId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            Object tenantId = jwtAuth.getToken().getClaim(SecurityConstants.TENANT_ID_CLAIM);
            return tenantId == null ? Optional.empty() : Optional.of(String.valueOf(tenantId));
        }
        if (authentication instanceof UsernamePasswordAuthenticationToken token
                && token.getDetails() instanceof String tenantId) {
            return Optional.of(tenantId);
        }
        return Optional.empty();
    }
}
