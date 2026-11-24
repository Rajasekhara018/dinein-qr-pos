package com.heuristiq.audit.config.security;

import static com.heuristiq.audit.config.security.SecurityConstants.*;
import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Authenticates an admin/public-chain request from its {@code Authorization: Bearer} token. An unusable token
 * never escapes the filter: the request continues unauthenticated, so protected endpoints answer 401 and public
 * ones keep working. Tokens minted for services (role SERVICE) are NOT accepted here: they belong to the
 * internal chain only. Not a Spring bean on purpose: it is added to the admin filter chain by
 * {@link SecurityConfig}, so it never runs on the internal chain.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final MeterRegistry meterRegistry;

    public JwtAuthenticationFilter(JwtService jwtService, MeterRegistry meterRegistry) {
        this.jwtService = jwtService;
        this.meterRegistry = meterRegistry;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            try {
                Claims claims = jwtService.parse(header.substring(BEARER_PREFIX.length()));
                if (TokenRoles.isService(claims)) {
                    meterRegistry.counter(JWT_FAILURE_METRIC, METRIC_REASON_TAG, SERVICE_TOKEN_ON_USER_API).increment();
                } else {
                    java.util.Set<SimpleGrantedAuthority> authorities = TokenRoles.of(claims).stream()
                            .map(role -> role.startsWith(ROLE_PREFIX) ? role : ROLE_PREFIX + role)
                            .map(SimpleGrantedAuthority::new)
                            .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
                    var auth = new UsernamePasswordAuthenticationToken(claims.getSubject(), null, authorities);
                    auth.setDetails(callerDetails(claims, request));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } catch (JwtException | IllegalArgumentException e) {
                meterRegistry.counter(JWT_FAILURE_METRIC, METRIC_REASON_TAG, e.getClass().getSimpleName()).increment();
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }

    /**
     * The organization comes from the verified token only. The cross-organization view needs both headers: the
     * gateway sets {@code X-Org-Wide} from the token, and the caller must also ask for it explicitly.
     */
    static CallerDetails callerDetails(Claims claims, HttpServletRequest request) {
        Object claim = claims.get(TENANT_CLAIM);
        String tenantId = claim == null || String.valueOf(claim).isBlank() ? null : String.valueOf(claim).trim();
        boolean all = "true".equalsIgnoreCase(request.getHeader(ORG_WIDE_HEADER))
                && "true".equalsIgnoreCase(request.getHeader(VIEW_ALL_ORGANIZATIONS_HEADER));
        return new CallerDetails(tenantId, all);
    }
}
