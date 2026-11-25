package com.heuristiq.identity.orgstructure.adapter.in;

import com.heuristiq.identity.organization.OrgWideConstants;
import com.heuristiq.identity.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Only an organization-wide caller may work in an organization other than their own. */
class CallerContextOrgSwitchTest {

    private final CallerContext context = new CallerContext("default");

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
        TenantContext.clear();
    }

    private void callerWith(String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("u1", null,
                java.util.Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));
        TenantContext.set("org-own");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Org-Id", "org-other");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @Test
    void anOrganizationWideCallerWorksInTheOrganizationTheyNamed() {
        callerWith("ROLE_SUPPORT", OrgWideConstants.AUTHORITY);

        assertThat(context.tenant()).isEqualTo("org-other");
    }

    @Test
    void everyoneElseStaysInTheirOwnOrganizationEvenWithTheSuperAdminRoleOrTheHeader() {
        callerWith("ROLE_SUPER_ADMIN");
        assertThat(context.tenant()).isEqualTo("org-own");

        callerWith("ROLE_ADMIN");
        assertThat(context.tenant()).isEqualTo("org-own");
    }

    @Test
    void aSignedInCallerWithNoOrganizationIsRefusedNotPlacedInTheDefaultOne() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("u1", null, List.of()));

        org.assertj.core.api.Assertions.assertThatThrownBy(context::tenant)
                .isInstanceOf(com.heuristiq.identity.orgstructure.domain.OrgStructureException.class);
    }
}
