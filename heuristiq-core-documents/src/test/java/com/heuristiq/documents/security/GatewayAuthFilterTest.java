package com.railcab.dms.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class GatewayAuthFilterTest {

    private static final String KEY = "forward-key";
    private final GatewayAuthFilter filter = new GatewayAuthFilter(KEY);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest request(String path) {
        MockHttpServletRequest r = new MockHttpServletRequest("GET", path);
        r.setServletPath(path);
        return r;
    }

    @Test
    void exemptPathsAreNotFiltered() {
        for (String p : new String[]{"/actuator/health", "/documents/public/abc", "/v3/api-docs", "/swagger-ui/index.html",
                "/api/v1/dms/internal/document-types/apply"}) {
            assertTrue(filter.shouldNotFilter(request(p)), p);
        }
        assertFalse(filter.shouldNotFilter(request("/api/v1/dms/documents")));
    }

    @Test
    void missingOrWrongTokenIsRejectedWithoutCallingChain() throws Exception {
        for (String token : new String[]{null, "", "wrong"}) {
            MockHttpServletRequest r = request("/api/v1/dms/documents");
            if (token != null) {
                r.addHeader(DmsSecurityConstants.GATEWAY_TOKEN_HEADER, token);
            }
            MockHttpServletResponse res = new MockHttpServletResponse();
            FilterChain chain = (a, b) -> fail("chain must not run");
            filter.doFilter(r, res, chain);
            assertEquals(401, res.getStatus());
            assertEquals(DmsSecurityConstants.UNAUTHORIZED_BODY, res.getContentAsString());
        }
    }

    @Test
    void validTokenAuthenticatesUserWithTenantAndStripsRolePrefix() throws Exception {
        MockHttpServletRequest r = request("/api/v1/dms/documents");
        r.addHeader(DmsSecurityConstants.GATEWAY_TOKEN_HEADER, KEY);
        r.addHeader(DmsSecurityConstants.USER_ID_HEADER, "u1");
        r.addHeader(DmsSecurityConstants.USER_ROLE_HEADER, "ROLE_ADMIN");
        r.addHeader(DmsSecurityConstants.TENANT_ID_HEADER, " org-a ");
        AtomicReference<Authentication> seen = new AtomicReference<>();
        filter.doFilter(r, new MockHttpServletResponse(),
                (a, b) -> seen.set(SecurityContextHolder.getContext().getAuthentication()));
        Authentication auth = seen.get();
        DmsPrincipal p = (DmsPrincipal) auth.getPrincipal();
        assertEquals("u1", p.userId());
        assertEquals("org-a", p.tenantId());
        assertEquals("ROLE_ADMIN", auth.getAuthorities().iterator().next().getAuthority());
        assertNull(SecurityContextHolder.getContext().getAuthentication(), "context cleared afterwards");
    }

    @Test
    void missingUserFallsBackToInternalServiceAndBlankRoleHasNoAuthority() throws Exception {
        MockHttpServletRequest r = request("/api/v1/dms/documents");
        r.addHeader(DmsSecurityConstants.GATEWAY_TOKEN_HEADER, KEY);
        AtomicReference<Authentication> seen = new AtomicReference<>();
        filter.doFilter(r, new MockHttpServletResponse(),
                (a, b) -> seen.set(SecurityContextHolder.getContext().getAuthentication()));
        DmsPrincipal p = (DmsPrincipal) seen.get().getPrincipal();
        assertEquals(DmsSecurityConstants.INTERNAL_SERVICE_USER, p.userId());
        assertTrue(p.isInternalService());
        assertNull(p.tenantId());

        MockHttpServletRequest r2 = request("/api/v1/dms/documents");
        r2.addHeader(DmsSecurityConstants.GATEWAY_TOKEN_HEADER, KEY);
        r2.addHeader(DmsSecurityConstants.USER_ID_HEADER, "u2");
        filter.doFilter(r2, new MockHttpServletResponse(),
                (a, b) -> seen.set(SecurityContextHolder.getContext().getAuthentication()));
        assertTrue(seen.get().getAuthorities().isEmpty());
    }
}
