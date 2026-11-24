package com.heuristiq.uam.security;

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
 * Private service-to-service credential, same scheme as the other Rider services' ServiceAuthenticationFilter:
 * header {@value SecurityConstants#INTERNAL_SERVICE_TOKEN_HEADER} must equal {@code rider.internal.service-token}.
 * Never given to browser or mobile clients. A blank configured secret rejects every caller (fail closed), so UAM
 * still starts without it.
 */
public class InternalServiceAuthenticationFilter extends OncePerRequestFilter {

    private final byte[] secret;

    public InternalServiceAuthenticationFilter(String secret) {
        this.secret = secret == null || secret.isBlank() ? null : secret.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String supplied = request.getHeader(SecurityConstants.INTERNAL_SERVICE_TOKEN_HEADER);
        if (secret != null && supplied != null
                && MessageDigest.isEqual(secret, supplied.getBytes(StandardCharsets.UTF_8))) {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    SecurityConstants.SERVICE_PRINCIPAL, null,
                    List.of(new SimpleGrantedAuthority(SecurityConstants.SERVICE_AUTHORITY))));
        }
        chain.doFilter(request, response);
    }
}
