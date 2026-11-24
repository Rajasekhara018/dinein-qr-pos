package com.heuristiq.notification.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heuristiq.notification.application.security.NmsPrincipal;
import com.heuristiq.notification.shared.CommonResponseObject;
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * Authenticates NMS's service-to-service endpoints ({@code /api/v1/nms/internal/**}), and only those. Two credentials are
 * accepted, either of which grants {@link NmsConfigConstants#SERVICE_AUTHORITY} and nothing else: the shared platform
 * service token in {@code X-Rider-Service-Token} (compared in constant time), or a bearer JWT signed with the platform
 * secret whose role is SERVICE.
 *
 * <p>The internal NMS API key ({@code X-NMS-API-Key}) is deliberately NOT accepted here: the gateway adds that key to every
 * request it forwards to NMS, users' requests included, so it proves nothing about the caller. {@link NmsAuthenticationFilter}
 * skips these paths. A request to an internal path without a valid credential gets 401 here.
 */
@Component
public class NmsInternalServiceAuthFilter extends OncePerRequestFilter {

    private final byte[] serviceToken;
    private final JwtSupport jwtSupport;
    private final ObjectMapper objectMapper;

    public NmsInternalServiceAuthFilter(InternalServiceAuthProperties properties, JwtSupport jwtSupport,
                                        ObjectMapper objectMapper) {
        this.serviceToken = StringUtils.hasText(properties.serviceToken())
                ? properties.serviceToken().getBytes(StandardCharsets.UTF_8) : null;
        this.jwtSupport = jwtSupport;
        this.objectMapper = objectMapper;
    }

    /** True for a path this filter is responsible for (and {@link NmsAuthenticationFilter} must skip). */
    public static boolean isInternalPath(String servletPath) {
        return servletPath != null && servletPath.startsWith(NmsConfigConstants.INTERNAL_PATH_PREFIX);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !isInternalPath(request.getServletPath());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!matchesServiceToken(request.getHeader(NmsConfigConstants.HEADER_SERVICE_TOKEN))
                && !hasServiceJwt(request.getHeader(HttpHeaders.AUTHORIZATION))) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(), CommonResponseObject.failure(NmsConfigConstants.UNAUTHORIZED_MESSAGE));
            return;
        }
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new NmsPrincipal(NmsConfigConstants.INTERNAL_USER, null, true), null,
                List.of(new SimpleGrantedAuthority(NmsConfigConstants.SERVICE_AUTHORITY))));
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
        return jwtSupport.parseBearer(authorization).map(JwtSupport.JwtPrincipal::isServiceToken).orElse(false);
    }
}
