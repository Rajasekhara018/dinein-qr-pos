package com.heuristiq.uam.security;

import com.heuristiq.uam.metrics.UamMetricNames;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {
    private JwtService jwtService;
    private SimpleMeterRegistry meters;
    private JwtAuthenticationFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockFilterChain chain;

    @BeforeEach
    void setUp() {
        jwtService = mock(JwtService.class);
        meters = new SimpleMeterRegistry();
        filter = new JwtAuthenticationFilter(jwtService, meters);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        chain = new MockFilterChain();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void run(String token) throws Exception {
        request.addHeader("Authorization", "Bearer " + token);
        filter.doFilter(request, response, chain);
    }

    private double failures(String reason) {
        return meters.counter(UamMetricNames.JWT_VALIDATION_FAILURES, UamMetricNames.TAG_REASON, reason).count();
    }

    @Test
    void requestWithoutBearerHeaderPassesThroughUnauthenticated() throws Exception {
        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(jwtService, never()).parse(any());
    }

    @Test
    void nonBearerSchemeIsIgnored() throws Exception {
        request.addHeader("Authorization", "Basic abc");
        filter.doFilter(request, response, chain);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(jwtService, never()).parse(any());
    }

    @Test
    void validTokenAuthenticatesWithPrefixedRolesAndTenantDetails() throws Exception {
        Claims claims = Jwts.claims().subject("user-1").add("roles", List.of("ADMIN", "ROLE_OPS"))
                .add("tenantId", "orgA").build();
        when(jwtService.parse("tok")).thenReturn(claims);

        run("tok");

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth.getName()).isEqualTo("user-1");
        assertThat(auth.getAuthorities()).extracting(Object::toString).containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_OPS");
        assertThat(auth.getDetails()).isEqualTo("orgA");
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void singleRoleClaimIsUsedWhenNoRolesListAndTenantMayBeAbsent() throws Exception {
        when(jwtService.parse("tok")).thenReturn(Jwts.claims().subject("u").add("role", "CUSTOMER").build());
        run("tok");
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_CUSTOMER");
        assertThat(auth.getDetails()).isNull();
    }

    @Test
    void tokenWithoutAnyRoleGetsNoAuthorities() throws Exception {
        when(jwtService.parse("tok")).thenReturn(Jwts.claims().subject("u").build());
        run("tok");
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities()).isEmpty();
    }

    @Test
    void orgWideClaimAddsOrgWideAuthority() throws Exception {
        when(jwtService.parse("tok")).thenReturn(Jwts.claims().subject("u").add("roles", List.of("ADMIN"))
                .add("orgWide", true).build());
        run("tok");
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting(Object::toString).contains("ORG_WIDE");
    }

    @Test
    void aServiceTokenIsNotHeldToTheUserSessionPolicy() throws Exception {
        org.springframework.test.util.ReflectionTestUtils.setField(filter, "policyEnforcer",
                mock(com.heuristiq.uam.securitypolicy.SecurityPolicyEnforcer.class));
        when(jwtService.parse("svc")).thenReturn(Jwts.claims().subject("wallet-service").build());

        run("svc");

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("wallet-service");
    }

    @Test
    void aUserTokenWithoutASessionIsStillRefusedWhenThePolicyIsOn() throws Exception {
        org.springframework.test.util.ReflectionTestUtils.setField(filter, "policyEnforcer",
                mock(com.heuristiq.uam.securitypolicy.SecurityPolicyEnforcer.class));
        when(jwtService.parse("usr")).thenReturn(Jwts.claims().subject("user-1").add("roles", List.of("ADMIN"))
                .add("userType", "OPS").build());

        run("usr");

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void rejectedTokensAreCountedByReasonAndLeaveRequestUnauthenticated() throws Exception {
        when(jwtService.parse("expired")).thenThrow(new ExpiredJwtException(null, null, "expired"));
        when(jwtService.parse("bad")).thenThrow(new MalformedJwtException("bad"));
        when(jwtService.parse("sig")).thenThrow(new SignatureException("sig"));
        when(jwtService.parse("boom")).thenThrow(new IllegalStateException("boom"));

        for (String token : List.of("expired", "bad", "sig", "boom")) {
            request = new MockHttpServletRequest();
            chain = new MockFilterChain();
            run(token);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            assertThat(chain.getRequest()).as("request continues for %s", token).isNotNull();
        }

        assertThat(failures(UamMetricNames.JWT_FAILURE_EXPIRED)).isEqualTo(1.0);
        assertThat(failures(UamMetricNames.JWT_FAILURE_MALFORMED)).isEqualTo(1.0);
        assertThat(failures(UamMetricNames.JWT_FAILURE_INVALID_SIGNATURE)).isEqualTo(1.0);
        assertThat(failures(UamMetricNames.JWT_FAILURE_OTHER)).isEqualTo(1.0);
    }
}
