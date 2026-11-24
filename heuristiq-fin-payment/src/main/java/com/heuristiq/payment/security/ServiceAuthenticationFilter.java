package com.heuristiq.paymentservice.security;

import com.heuristiq.paymentservice.shared.PaymentCommonConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * Private service-to-service credential (header {@code X-Rider-Service-Token} vs property
 * {@code rider.internal.service-token}); never supplied to browser or mobile clients. Same scheme
 * as Rider_RideService's ServiceAuthenticationFilter. Only installed on the /api/v1/internal/**
 * filter chain (see SecurityConfig) — it grants nothing on the end-user API.
 */
public class ServiceAuthenticationFilter extends OncePerRequestFilter {

    public static final String HEADER = PaymentCommonConstants.SERVICE_TOKEN_HEADER;
    /** The organization a backend service acts for, when it is acting for a user's session; only honoured with a valid service token. */
    public static final String TENANT_HEADER = PaymentCommonConstants.TENANT_HEADER;

    private final String secret;

    public ServiceAuthenticationFilter(String secret) {
        this.secret = secret;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String supplied = request.getHeader(HEADER);
        if (supplied != null && secret != null && !secret.isBlank()
                && MessageDigest.isEqual(secret.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8))) {
            String tenantId = request.getHeader(TENANT_HEADER);
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    new AuthPrincipal(SecurityConstants.INTERNAL_SERVICE_USER_ID, AuthPrincipal.SERVICE_ROLE, false, tenantId == null || tenantId.isBlank() ? null : tenantId.trim()), null,
                    List.of(new SimpleGrantedAuthority(SecurityConstants.SERVICE_AUTHORITY))));
        }
        chain.doFilter(request, response);
    }
}
