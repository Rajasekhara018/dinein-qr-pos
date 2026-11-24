package com.heuristiq.notification.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.heuristiq.notification.application.security.NmsPrincipal;
import com.heuristiq.notification.application.security.TenantAccess;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/** Where the caller's organization comes from: the token claim, or a header honoured only after a backend credential. */
class NmsAuthenticationFilterTenantTest {

    private static final String JWT_SECRET = "unit-test-secret-unit-test-secret-unit-test-secret";
    private static final String INTERNAL_KEY = "internal-key";

    private NmsAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        NmsProperties props = new NmsProperties();
        props.getSecurity().setEnabled(true);
        props.getSecurity().setInternalApiKey(INTERNAL_KEY);
        JwtSupport jwtSupport = new JwtSupport();
        ReflectionTestUtils.setField(jwtSupport, "jwtSecret", JWT_SECRET);
        filter = new NmsAuthenticationFilter(props, new ObjectMapper().registerModule(new JavaTimeModule()), jwtSupport);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static String token(String tenantClaim, String role) {
        SecretKey key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));
        var builder = Jwts.builder().subject("u-1").claim("roles", List.of(role));
        if (tenantClaim != null) {
            builder.claim("tenantId", tenantClaim);
        }
        return builder.signWith(key).compact();
    }

    private NmsPrincipal run(MockHttpServletRequest request) throws Exception {
        request.setServletPath("/api/v1/nms/logs");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        assertThat(response.getStatus()).isEqualTo(200);
        return (NmsPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    @Test
    void aPersonsOrganizationIsTheTenantIdClaimOfTheirToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token("org-a", "CUSTOMER"));

        NmsPrincipal principal = run(request);

        assertThat(principal.userId()).isEqualTo("u-1");
        assertThat(principal.tenantId()).isEqualTo("org-a");
        assertThat(principal.internalService()).isFalse();
    }

    @Test
    void aPersonCannotChooseAnOrganizationWithAHeader() throws Exception {
        MockHttpServletRequest withClaim = new MockHttpServletRequest();
        withClaim.addHeader("Authorization", "Bearer " + token("org-a", "CUSTOMER"));
        withClaim.addHeader("X-Tenant-Id", "org-b");
        assertThat(run(withClaim).tenantId()).isEqualTo("org-a");

        MockHttpServletRequest withoutClaim = new MockHttpServletRequest();
        withoutClaim.addHeader("Authorization", "Bearer " + token(null, "CUSTOMER"));
        withoutClaim.addHeader("X-Tenant-Id", "org-b");
        NmsPrincipal principal = run(withoutClaim);
        assertThat(principal.tenantId()).isNull();
        assertThat(TenantAccess.canSee("org-b")).isFalse();
    }

    @Test
    void theInternalKeyHonoursTheOrganizationHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-NMS-API-Key", INTERNAL_KEY);
        request.addHeader("X-Tenant-Id", " org-a ");

        NmsPrincipal principal = run(request);

        assertThat(principal.internalService()).isTrue();
        assertThat(principal.tenantId()).isEqualTo("org-a");
    }

    @Test
    void theInternalKeyWithoutAnOrganizationHeaderIsNotLimited() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-NMS-API-Key", INTERNAL_KEY);

        NmsPrincipal principal = run(request);

        assertThat(principal.tenantId()).isNull();
        assertThat(TenantAccess.scope().unrestricted()).isTrue();
    }

    @Test
    void aWrongInternalKeyDoesNotEarnTheOrganizationHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/api/v1/nms/logs");
        request.addHeader("X-NMS-API-Key", "wrong");
        request.addHeader("X-Tenant-Id", "org-a");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void aServiceTokenWithoutAClaimMayNameTheOrganizationByHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token(null, "SERVICE"));
        request.addHeader("X-Tenant-Id", "org-a");

        NmsPrincipal principal = run(request);

        assertThat(principal.internalService()).isTrue();
        assertThat(principal.tenantId()).isEqualTo("org-a");
    }
}
