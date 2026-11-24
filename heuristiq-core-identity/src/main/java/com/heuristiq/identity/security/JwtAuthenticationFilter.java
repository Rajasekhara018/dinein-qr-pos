package com.heuristiq.uam.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import com.heuristiq.uam.metrics.UamMetricNames;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final MeterRegistry meterRegistry;
    @org.springframework.beans.factory.annotation.Autowired
    private com.heuristiq.uam.securitypolicy.SecurityPolicyEnforcer policyEnforcer;

    public JwtAuthenticationFilter(JwtService jwtService, MeterRegistry meterRegistry) {
        this.jwtService = jwtService;
        this.meterRegistry = meterRegistry;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader(SecurityConstants.AUTHORIZATION_HEADER);
        if (header != null && header.startsWith(SecurityConstants.BEARER_PREFIX)) {
            // An unusable token (expired / malformed / bad signature) must not escape the filter:
            // rethrowing it made Tomcat log a stack trace and answer 500. Count it, then continue
            // UNAUTHENTICATED -- protected endpoints get Spring Security's normal 401, and public
            // ones (e.g. login, sent with a stale token still in the client) keep working.
            Claims claims;
            try {
                claims = jwtService.parse(header.substring(SecurityConstants.BEARER_PREFIX.length()));
            } catch (ExpiredJwtException e) {
                rejected(UamMetricNames.JWT_FAILURE_EXPIRED);
                filterChain.doFilter(request, response);
                return;
            } catch (MalformedJwtException e) {
                rejected(UamMetricNames.JWT_FAILURE_MALFORMED);
                filterChain.doFilter(request, response);
                return;
            } catch (SignatureException e) {
                rejected(UamMetricNames.JWT_FAILURE_INVALID_SIGNATURE);
                filterChain.doFilter(request, response);
                return;
            } catch (RuntimeException e) {
                rejected(UamMetricNames.JWT_FAILURE_OTHER);
                filterChain.doFilter(request, response);
                return;
            }
            // Enrollment proofs are for the MFA enrollment flow, never for authenticating API requests.
            // They also have no roles or session id, so reject them before identifying service tokens.
            if ("MFA_ENROLLMENT".equals(claims.get("purpose"))) {
                rejected(UamMetricNames.JWT_FAILURE_OTHER);
                filterChain.doFilter(request, response);
                return;
            }
            // Service-to-service tokens (signed with the shared secret, no roles, no user type, no session id; e.g. the wallet's
            // user-directory lookups) are not user sign-ins, so the per-session security policy does not apply to them.
            boolean serviceToken = claims.get("sid") == null && claims.get("userType") == null && extractRoles(claims).isEmpty();
            if (policyEnforcer != null && !serviceToken) {
                try {
                    if (claims.get("sid") == null)
                        throw new com.heuristiq.uam.common.UamException("Sign in to start a policy-enforced session", org.springframework.http.HttpStatus.UNAUTHORIZED);
                    var session = policyEnforcer.requireSession(String.valueOf(claims.get("sid")), claims.getSubject());
                    String tenant = claims.get(SecurityConstants.TENANT_ID_CLAIM) == null ? ""
                            : String.valueOf(claims.get(SecurityConstants.TENANT_ID_CLAIM));
                    if (!tenant.equals(session.getMetadata().getOrDefault("policyTenantId", "")))
                        throw new com.heuristiq.uam.common.UamException("Organization context changed", org.springframework.http.HttpStatus.UNAUTHORIZED);
                    request.setAttribute("uam.policySessionId", String.valueOf(claims.get("sid")));
                } catch (com.heuristiq.uam.common.UamException rejectedPolicy) {
                    SecurityContextHolder.clearContext();
                    filterChain.doFilter(request, response);
                    return;
                }
            }
            Collection<SimpleGrantedAuthority> authorities = extractRoles(claims).stream()
                    .map(role -> role.startsWith(SecurityConstants.ROLE_AUTHORITY_PREFIX)
                            ? role : SecurityConstants.ROLE_AUTHORITY_PREFIX + role)
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toSet());
            // An organization-wide caller (the token's orgWide claim, set at sign-in from the privilege) may work in other
            // organizations; the authority carries that to the services that decide it (e.g. the org structure).
            if (Boolean.TRUE.equals(claims.get(com.heuristiq.uam.organization.OrgWideConstants.CLAIM))) {
                authorities.add(new SimpleGrantedAuthority(com.heuristiq.uam.organization.OrgWideConstants.AUTHORITY));
            }
            var auth = new UsernamePasswordAuthenticationToken(claims.getSubject(), null, authorities);
            Object tenantId = claims.get(SecurityConstants.TENANT_ID_CLAIM);
            auth.setDetails(tenantId == null ? null : String.valueOf(tenantId));
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
        filterChain.doFilter(request, response);
    }

    private void rejected(String reason) {
        meterRegistry.counter(UamMetricNames.JWT_VALIDATION_FAILURES, UamMetricNames.TAG_REASON, reason).increment();
        SecurityContextHolder.clearContext();
    }

    @SuppressWarnings("unchecked")
    private List<String> extractRoles(Claims claims) {
        Object roles = claims.get(SecurityConstants.ROLES_CLAIM);
        if (roles instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        Object role = claims.get(SecurityConstants.ROLE_CLAIM);
        return role == null ? List.of() : List.of(String.valueOf(role));
    }
}
