package com.heuristiq.uam.orgstructure.adapter.in;

import com.heuristiq.uam.orgstructure.domain.OrgStructureException;
import com.heuristiq.uam.orgstructure.domain.ScopeMode;
import com.heuristiq.uam.organization.OrgWideConstants;
import com.heuristiq.uam.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Who is calling and what they may reach; complements CallerContextOrgSwitchTest. */
class CallerContextTest {

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
        TenantContext.clear();
    }

    private void signIn(String name, String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(name, null,
                Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));
    }

    @Test
    void identityHelpersAreEmptyWhenNobodyIsSignedIn() {
        assertThat(CallerContext.roles()).isEmpty();
        assertThat(CallerContext.userId()).isNull();
        assertThat(CallerContext.actor(null)).isNull();
        assertThat(CallerContext.isOrgWide()).isFalse();
        assertThat(CallerContext.isSuperAdmin()).isFalse();
        assertThat(CallerContext.positionIndependentScope()).isNull();
    }

    @Test
    void rolesDropTheRolePrefixAndUserIdIsTheTokenSubject() {
        signIn("user-7", "ROLE_HR", "ORG_WIDE", "SCOPE_read");

        assertThat(CallerContext.roles()).containsExactlyInAnyOrder("HR", "ORG_WIDE", "SCOPE_read");
        assertThat(CallerContext.userId()).isEqualTo("user-7");
        assertThat(CallerContext.actor(SecurityContextHolder.getContext().getAuthentication())).isEqualTo("user-7");
    }

    @Test
    void superAdminSeesTheWholeOrganizationRegardlessOfPosition() {
        signIn("u", "ROLE_SUPER_ADMIN");
        assertThat(CallerContext.isSuperAdmin()).isTrue();
        assertThat(CallerContext.positionIndependentScope()).isEqualTo(ScopeMode.ORG_WIDE);

        signIn("u", "ROLE_ADMIN");
        assertThat(CallerContext.isSuperAdmin()).isFalse();
        assertThat(CallerContext.positionIndependentScope()).isNull();
    }

    @Test
    void orgWideFlagComesOnlyFromTheOrgWideAuthority() {
        signIn("u", OrgWideConstants.AUTHORITY);
        assertThat(CallerContext.isOrgWide()).isTrue();
        signIn("u", "ROLE_SUPER_ADMIN");
        assertThat(CallerContext.isOrgWide()).isFalse();
    }

    @Test
    void tenantComesFromTheTokenThenTheDefault() {
        CallerContext context = new CallerContext("default");
        TenantContext.set("org-own");
        assertThat(context.tenant()).isEqualTo("org-own");

        TenantContext.set("  ");
        assertThat(context.tenant()).isEqualTo("default");
        TenantContext.clear();
        assertThat(context.tenant()).isEqualTo("default");
    }

    @Test
    void theDefaultOrganizationIsResolvedByCodeSoAGeneratedMongoIdWorksToo() {
        for (String id : new String[] {"default", "65f0c0ffee0000000000abcd"}) {
            CallerContext context = CallerContext.withDefault(() -> id);
            assertThat(context.tenant()).isEqualTo(id);
        }
        assertThatThrownBy(() -> CallerContext.withDefault(() -> null).tenant()).isInstanceOf(OrgStructureException.class);
    }

    @Test
    void aSignedInCallerWithoutATenantClaimIsRefusedNeverPlacedInTheDefaultOrganization() {
        signIn("u", "ROLE_ADMIN");
        CallerContext context = CallerContext.withDefault(() -> "65f0c0ffee0000000000abcd");

        assertThatThrownBy(context::tenant).isInstanceOf(OrgStructureException.class);
        assertThatThrownBy(() -> new CallerContext("default").tenant()).isInstanceOf(OrgStructureException.class);

        TenantContext.set("org-own");
        assertThat(context.tenant()).isEqualTo("org-own");
    }

    @Test
    void callerWithNoOrganizationAndNoDefaultIsRejected() {
        assertThatThrownBy(() -> new CallerContext(null).tenant()).isInstanceOf(OrgStructureException.class);
        assertThatThrownBy(() -> new CallerContext(" ").tenant()).isInstanceOf(OrgStructureException.class);
    }

    @Test
    void orgWideCallerWithoutOrBlankHeaderStaysInOwnOrganization() {
        signIn("u", OrgWideConstants.AUTHORITY);
        TenantContext.set("org-own");
        CallerContext context = new CallerContext("default");

        // no request bound at all
        assertThat(context.tenant()).isEqualTo("org-own");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Org-Id", "   ");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        assertThat(context.tenant()).isEqualTo("org-own");

        MockHttpServletRequest withHeader = new MockHttpServletRequest();
        withHeader.addHeader("X-Org-Id", "  org-x ");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(withHeader));
        assertThat(context.tenant()).isEqualTo("org-x");

        MockHttpServletRequest noHeader = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(noHeader));
        assertThat(context.tenant()).isEqualTo("org-own");
    }
}
