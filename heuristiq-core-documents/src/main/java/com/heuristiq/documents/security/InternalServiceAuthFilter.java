package com.railcab.dms.security;

import com.railcab.dms.common.DmsCommonConstants;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * Authenticates the service-to-service paths ({@code /api/v1/dms/internal/**}), and only those. Two credentials are
 * accepted, either of which grants the {@link DmsSecurityConstants#SERVICE_AUTHORITY} authority and nothing else: the shared header
 * {@code X-Rider-Service-Token} (compared in constant time), or a bearer JWT signed with the platform secret whose
 * role is SERVICE, which is what Rider_WorkflowService sends on the apply callback. The gateway's forward key is not
 * involved: Workflow calls DMS directly, not through the gateway, and the gateway refuses these paths anyway.
 *
 * <p>Any other path is left to {@link GatewayAuthFilter}. A request to an internal path without a valid credential
 * gets 401 here, whatever else it carries.
 */
@Component
public class InternalServiceAuthFilter extends OncePerRequestFilter {

    private final byte[] serviceToken;
    private final SecretKey jwtKey;

    public InternalServiceAuthFilter(InternalAuthProperties properties) {
        this.serviceToken = StringUtils.hasText(properties.serviceToken())
                ? properties.serviceToken().getBytes(StandardCharsets.UTF_8) : null;
        byte[] secret = properties.jwtSecret() == null ? new byte[0] : properties.jwtSecret().getBytes(StandardCharsets.UTF_8);
        this.jwtKey = secret.length >= DmsSecurityConstants.MIN_SECRET_BYTES ? Keys.hmacShaKeyFor(secret) : null;
    }

    /** True for a path this filter is responsible for (and {@link GatewayAuthFilter} must skip). */
    public static boolean isInternalPath(String servletPath) {
        return servletPath != null && servletPath.startsWith(DmsSecurityConstants.INTERNAL_PATH_PREFIX);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !isInternalPath(request.getServletPath());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!matchesServiceToken(request.getHeader(DmsCommonConstants.SERVICE_TOKEN_HEADER)) && !hasServiceJwt(request.getHeader(HttpHeaders.AUTHORIZATION))) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(DmsSecurityConstants.UNAUTHORIZED_BODY);
            return;
        }
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new DmsPrincipal(DmsSecurityConstants.CALLER_NAME, DmsSecurityConstants.INTERNAL_SERVICE_ROLE, null), null,
                List.of(new SimpleGrantedAuthority(DmsSecurityConstants.SERVICE_AUTHORITY))));
        try {
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private boolean matchesServiceToken(String supplied) {
        return serviceToken != null && supplied != null
                && MessageDigest.isEqual(serviceToken, supplied.getBytes(StandardCharsets.UTF_8));
    }

    private boolean hasServiceJwt(String authorization) {
        if (jwtKey == null || authorization == null || !authorization.startsWith(DmsCommonConstants.BEARER_PREFIX)) {
            return false;
        }
        try {
            Claims claims = Jwts.parser().verifyWith(jwtKey).build().parseSignedClaims(authorization.substring(DmsCommonConstants.BEARER_PREFIX.length())).getPayload();
            return roles(claims).stream().anyMatch(r -> DmsSecurityConstants.SERVICE_ROLE.equals(r)
                    || (DmsSecurityConstants.ROLE_PREFIX + DmsSecurityConstants.SERVICE_ROLE).equals(r));
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private static List<String> roles(Claims claims) {
        Object roles = claims.get(DmsSecurityConstants.CLAIM_ROLES);
        if (roles instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        Object role = claims.get(DmsSecurityConstants.CLAIM_ROLE);
        return role == null ? List.of() : List.of(String.valueOf(role));
    }
}
