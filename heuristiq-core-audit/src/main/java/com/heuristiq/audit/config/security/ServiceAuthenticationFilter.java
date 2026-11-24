package com.railcab.audit.config.security;

import static com.railcab.audit.config.security.SecurityConstants.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Service-to-service authentication for the internal chain. Two credentials are accepted, both of which grant
 * only ROLE_SERVICE and only on {@code /api/v1/audit/internal/**}:
 * <ul>
 * <li>the shared header {@code X-Rider-Service-Token} compared with {@code rider.internal.service-token}
 * (same scheme as Rider_PaymentService / Rider_RideService), used by services that read settings;</li>
 * <li>a bearer JWT signed with the shared secret whose role is SERVICE, which is what Rider_WorkflowService
 * sends when it calls the apply callback.</li>
 * </ul>
 * Not a Spring bean on purpose (it would otherwise be registered on every chain).
 */
public class ServiceAuthenticationFilter extends OncePerRequestFilter {

    private final String secret;
    private final JwtService jwtService;

    public ServiceAuthenticationFilter(String secret, JwtService jwtService) {
        this.secret = secret;
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (matchesSharedToken(request.getHeader(SERVICE_TOKEN_HEADER)) || hasServiceJwt(request.getHeader(AUTHORIZATION_HEADER))) {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    INTERNAL_PRINCIPAL, null, List.of()));
        }
        chain.doFilter(request, response);
    }

    private boolean matchesSharedToken(String supplied) {
        return supplied != null && secret != null && !secret.isBlank()
                && MessageDigest.isEqual(secret.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8));
    }

    private boolean hasServiceJwt(String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return false;
        }
        try {
            Claims claims = jwtService.parse(authorization.substring(BEARER_PREFIX.length()));
            return TokenRoles.isService(claims);
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
