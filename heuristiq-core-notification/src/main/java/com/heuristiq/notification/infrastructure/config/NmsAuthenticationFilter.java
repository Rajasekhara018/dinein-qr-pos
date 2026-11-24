package com.heuristiq.notification.infrastructure.config;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heuristiq.notification.application.security.NmsPrincipal;
import com.heuristiq.notification.shared.CommonResponseObject;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

import static com.heuristiq.notification.infrastructure.config.NmsConfigConstants.*;

@Component
@RequiredArgsConstructor
public class NmsAuthenticationFilter extends OncePerRequestFilter {

    private final NmsProperties props;
    private final ObjectMapper objectMapper;
    private final JwtSupport jwtSupport;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // getServletPath(), not getRequestURI(): with server.forward-headers-strategy=framework the
        // gateway's X-Forwarded-Prefix (/api/nms) is folded into the request URI, so the exempt
        // paths below never matched a proxied request (401 on /v3/api-docs and in-app/ws/info).
        String path = request.getServletPath();
        boolean docsRequest = path.startsWith(API_DOCS_PATH)
                || path.startsWith(SWAGGER_UI_PATH);
        return !props.getSecurity().isEnabled()
                // NmsSecurityConfig's authorizeHttpRequests already permitAll()s /actuator/** so
                // Prometheus scraping doesn't need app-level auth -- but this filter runs BEFORE
                // that authorization decision (addFilterBefore), so it must exempt the same path
                // itself or it 401s the request before the authorization rule ever applies.
                || path.startsWith(ACTUATOR_PATH_PREFIX)
                || path.startsWith(IN_APP_WS_PATH)
                || path.startsWith(CALLBACKS_PATH_PREFIX)
                // Service-to-service endpoints: NmsInternalServiceAuthFilter authenticates them (never with the API key).
                || NmsInternalServiceAuthFilter.isInternalPath(path)
                || path.startsWith(JAVADOC_PATH)
                || (props.getSecurity().isExposeDocs() && docsRequest);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (authenticateInternalKey(request) || authenticateBearerToken(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), CommonResponseObject.failure(UNAUTHORIZED_MESSAGE));
    }

    private boolean authenticateInternalKey(HttpServletRequest request) {
        String expected = props.getSecurity().getInternalApiKey();
        if (!StringUtils.hasText(expected)) return false;
        String actual = request.getHeader(HEADER_INTERNAL_API_KEY);
        if (!expected.equals(actual)) return false;
        // The organization header is honoured only here, after the internal key matched: an ordinary user can never set it.
        setAuthentication(new NmsPrincipal(INTERNAL_USER, tenantHeader(request), true),
                INTERNAL_ROLES, request);
        return true;
    }

    private boolean authenticateBearerToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        return jwtSupport.parseBearer(header)
                .map(principal -> {
                    // The organization of a person comes from the token claim only; a service token may name one by header.
                    String tenantId = principal.isServiceToken() && !StringUtils.hasText(principal.tenantId())
                            ? tenantHeader(request) : principal.tenantId();
                    setAuthentication(new NmsPrincipal(principal.userId(), tenantId, principal.isServiceToken()),
                            principal.roles(), request);
                    return true;
                })
                .orElse(false);
    }

    private static String tenantHeader(HttpServletRequest request) {
        String value = request.getHeader(HEADER_TENANT_ID);
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private void setAuthentication(NmsPrincipal principal, List<String> roles, HttpServletRequest request) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                roles.stream()
                        .map(role -> new SimpleGrantedAuthority(ROLE_PREFIX + role))
                        .toList());
        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
