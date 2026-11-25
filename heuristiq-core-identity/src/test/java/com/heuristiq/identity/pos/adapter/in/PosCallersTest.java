package com.heuristiq.identity.pos.adapter.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.heuristiq.identity.common.UamCommonConstants;
import com.heuristiq.identity.organization.OrgWideConstants;
import com.heuristiq.identity.organization.Tenant;
import com.heuristiq.identity.organization.repository.TenantRepository;
import com.heuristiq.identity.pos.application.PosCaller;
import com.heuristiq.identity.security.TenantContext;
import java.util.Arrays;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/** Who counts as tenant-wide, and which tenant the caller is placed in. */
class PosCallersTest {

    private PosCallers callers;

    @BeforeEach
    void setUp() {
        TenantRepository tenants = mock(TenantRepository.class);
        when(tenants.findById(any())).thenReturn(Optional.empty());
        when(tenants.findById("t1")).thenReturn(Optional.of(Tenant.builder().id("t1").code("ACME").build()));
        callers = new PosCallers(tenants);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static Authentication auth(String... authorities) {
        return new UsernamePasswordAuthenticationToken("u1", "n/a",
                Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList());
    }

    @Test
    void superAdminIsTenantWideOnPathTenant() {
        TenantContext.set("t1");
        assertThat(callers.current(auth(UamCommonConstants.SUPER_ADMIN_AUTHORITY), "t2")).isEqualTo(new PosCaller("u1", "t2", true));
    }

    @Test
    void orgWideCallerIsTenantWideOnPathTenant() {
        TenantContext.set("t1");
        assertThat(callers.current(auth(OrgWideConstants.AUTHORITY), "t2")).isEqualTo(new PosCaller("u1", "t2", true));
    }

    @Test
    void orgAdminRoleIsTenantWide() {
        TenantContext.set("t1");
        assertThat(callers.current(auth("ROLE_ACME_ADMIN"), "t1")).isEqualTo(new PosCaller("u1", "t1", true));
    }

    @Test
    void anotherTenantsAdminRoleIsNotTenantWide() {
        TenantContext.set("t1");
        assertThat(callers.current(auth("ROLE_GLOBEX_ADMIN"), "t1").tenantWide()).isFalse();
    }

    @Test
    void restaurantAdminRoleIsTenantWide() {
        TenantContext.set("t1");
        assertThat(callers.current(auth("ROLE_POS_RESTAURANT_ADMIN"), "t1").tenantWide()).isTrue();
    }

    @Test
    void cashierIsBranchScoped() {
        TenantContext.set("t1");
        assertThat(callers.current(auth("ROLE_CASHIER"), "t2")).isEqualTo(new PosCaller("u1", "t1", false));
    }
}
