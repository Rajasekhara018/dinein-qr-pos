package com.railcab.audit.config.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthenticationFilterTenantTest {

    private static Claims claims(Object tenantId) {
        var b = Jwts.claims().subject("u");
        if (tenantId != null) {
            b.add("tenantId", tenantId);
        }
        return b.build();
    }

    private static MockHttpServletRequest request(String orgWide, String viewAll) {
        MockHttpServletRequest r = new MockHttpServletRequest();
        if (orgWide != null) {
            r.addHeader("X-Org-Wide", orgWide);
        }
        if (viewAll != null) {
            r.addHeader("X-View-All-Organizations", viewAll);
        }
        return r;
    }

    @Test
    void organizationComesFromTheTokenClaim() {
        assertThat(JwtAuthenticationFilter.callerDetails(claims("org-a"), request(null, null)))
                .isEqualTo(new CallerDetails("org-a", false));
    }

    @Test
    void noClaimOrBlankClaimMeansNoOrganization() {
        assertThat(JwtAuthenticationFilter.callerDetails(claims(null), request(null, null)).tenantId()).isNull();
        assertThat(JwtAuthenticationFilter.callerDetails(claims("  "), request(null, null)).tenantId()).isNull();
    }

    @Test
    void crossOrganizationViewNeedsBothTheGatewayMarkAndTheAsk() {
        assertThat(JwtAuthenticationFilter.callerDetails(claims("o"), request("true", "true")).allOrganizations()).isTrue();
        assertThat(JwtAuthenticationFilter.callerDetails(claims("o"), request("true", null)).allOrganizations()).isFalse();
        assertThat(JwtAuthenticationFilter.callerDetails(claims("o"), request(null, "true")).allOrganizations()).isFalse();
        assertThat(JwtAuthenticationFilter.callerDetails(claims("o"), request("true", "false")).allOrganizations()).isFalse();
    }
}
