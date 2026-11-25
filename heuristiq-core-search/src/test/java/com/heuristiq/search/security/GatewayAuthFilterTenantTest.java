package com.heuristiq.search.security;

import com.heuristiq.search.scope.CallerScope;
import com.heuristiq.search.scope.CurrentCaller;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayAuthFilterTenantTest {

    private final GatewayAuthFilter filter = new GatewayAuthFilter("forward-key");
    private final CurrentCaller currentCaller = new CurrentCaller();

    private CallerScope callerSeen(MockHttpServletRequest request) throws Exception {
        AtomicReference<CallerScope> seen = new AtomicReference<>();
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> seen.set(currentCaller.current()));
        assertThat(response.getStatus()).isNotEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        return seen.get();
    }

    private static MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/search");
        request.addHeader("X-Gateway-Token", "forward-key");
        return request;
    }

    @Test
    void theOrganizationHeaderOfTheGatewayBecomesTheCallersOrganization() throws Exception {
        MockHttpServletRequest request = request();
        request.addHeader("X-User-Id", "u1");
        request.addHeader("X-User-Role", "MANAGER");
        request.addHeader("X-Tenant-Id", " org-7 ");

        assertThat(callerSeen(request)).isEqualTo(new CallerScope("org-7", false));
    }

    @Test
    void noOrganizationHeaderMeansNoOrganizationAndTheInternalCallerIsRecognised() throws Exception {
        MockHttpServletRequest user = request();
        user.addHeader("X-User-Id", "u1");
        user.addHeader("X-User-Role", "MANAGER");
        assertThat(callerSeen(user)).isEqualTo(new CallerScope(null, false));

        // No user headers at all: a request from the gateway's own internal calls.
        assertThat(callerSeen(request())).isEqualTo(new CallerScope(null, true));
    }

    @Test
    void lookingAcrossOrganizationsNeedsBothTheGatewaysOrgWideMarkAndTheCallersRequest() throws Exception {
        MockHttpServletRequest both = request();
        both.addHeader("X-User-Id", "u1");
        both.addHeader("X-User-Role", "SUPER_ADMIN");
        both.addHeader("X-Tenant-Id", "org-7");
        both.addHeader("X-Org-Wide", "true");
        both.addHeader("X-View-All-Organizations", "true");
        assertThat(callerSeen(both).allOrganizations()).isTrue();

        MockHttpServletRequest notAsked = request();
        notAsked.addHeader("X-User-Id", "u1");
        notAsked.addHeader("X-Tenant-Id", "org-7");
        notAsked.addHeader("X-Org-Wide", "true");
        assertThat(callerSeen(notAsked).allOrganizations()).isFalse();

        // Asking without the gateway's mark (an ordinary user) changes nothing.
        MockHttpServletRequest notWide = request();
        notWide.addHeader("X-User-Id", "u1");
        notWide.addHeader("X-Tenant-Id", "org-7");
        notWide.addHeader("X-View-All-Organizations", "true");
        assertThat(callerSeen(notWide).allOrganizations()).isFalse();
    }
}
