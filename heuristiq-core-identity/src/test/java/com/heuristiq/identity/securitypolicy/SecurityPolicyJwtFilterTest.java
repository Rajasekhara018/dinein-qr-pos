package com.heuristiq.identity.securitypolicy;

import com.heuristiq.identity.config.UamProperties;
import com.heuristiq.identity.security.JwtAuthenticationFilter;
import com.heuristiq.identity.security.JwtService;
import com.heuristiq.identity.security.SessionAccess;
import com.heuristiq.identity.session.UserSession;
import com.heuristiq.identity.users.UamUser;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SecurityPolicyJwtFilterTest {
    final JwtService jwt;
    final SecurityPolicyEnforcer policies = mock(SecurityPolicyEnforcer.class);
    final JwtAuthenticationFilter filter;
    SecurityPolicyJwtFilterTest() {
        var properties = new UamProperties();
        properties.getAuth().setJwtSecret("a-long-test-secret-with-at-least-thirty-two-bytes!!");
        jwt = new JwtService(properties);
        filter = new JwtAuthenticationFilter(jwt, new SimpleMeterRegistry());
        ReflectionTestUtils.setField(filter, "policyEnforcer", policies);
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    void request(String token) throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {});
    }
    @Test void enrollmentProofCannotAuthenticateProtectedRequests() throws Exception {
        request(jwt.generateEnrollment("user", "", ""));
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(policies);
    }
    @Test void aSessionTokenForTheFormerOrganizationCannotSurviveASwitch() throws Exception {
        var user = UamUser.builder().id("user").roles(List.of("OPS")).build();
        String token = jwt.generateSession(user, List.of(), "old-org", false, SessionAccess.unchanged(user.getRoles()), "sid");
        var session = UserSession.builder().sessionId("sid").metadata(Map.of("policyTenantId", "new-org")).build();
        when(policies.requireSession("sid", "user")).thenReturn(session);
        request(token);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
    @Test void matchingValidatedSessionAuthenticatesTheUser() throws Exception {
        var user = UamUser.builder().id("user").roles(List.of("OPS")).build();
        String token = jwt.generateSession(user, List.of(), "org", false, SessionAccess.unchanged(user.getRoles()), "sid");
        when(policies.requireSession("sid", "user")).thenReturn(UserSession.builder().sessionId("sid").metadata(Map.of("policyTenantId", "org")).build());
        request(token);
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("user");
    }
}
