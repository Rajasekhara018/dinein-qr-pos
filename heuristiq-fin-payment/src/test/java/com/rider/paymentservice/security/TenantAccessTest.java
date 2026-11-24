package com.heuristiq.paymentservice.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Who may reach whose payment records. */
class TenantAccessTest {

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static void as(AuthPrincipal principal) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    @Test
    void aCallerWithAnOrganizationReachesThatOrganizationsRecordsAndUnownedOnes() {
        as(new AuthPrincipal("u1", "CUSTOMER", false, "org-a"));

        assertThat(TenantAccess.canSee("org-a")).isTrue();
        assertThat(TenantAccess.canSee("org-b")).isFalse();
        assertThat(TenantAccess.canSee(null)).isFalse();
        assertThat(TenantAccess.stamp()).isEqualTo("org-a");
    }

    @Test
    void anElevatedAdminIsStillLimitedToTheirOwnOrganization() {
        as(new AuthPrincipal("admin", "ADMIN", true, "org-a"));

        assertThat(TenantAccess.canSee("org-b")).isFalse();
        assertThat(TenantAccess.jpqlClause("p")).isEqualTo(" AND p.tenantId = :tenantId");
    }

    @Test
    void aBackendServiceWithNoOrganizationIsNotLimitedButOneActingForAnOrganizationIs() {
        as(new AuthPrincipal("internal-service", "SERVICE", false, null));
        assertThat(TenantAccess.canSee("org-b")).isTrue();
        assertThat(TenantAccess.jpqlClause("p")).isEmpty();
        assertThat(TenantAccess.stamp()).isNull();

        as(new AuthPrincipal("internal-service", "SERVICE", false, "org-a"));
        assertThat(TenantAccess.canSee("org-b")).isFalse();
        assertThat(TenantAccess.canSee("org-a")).isTrue();
    }

    @Test
    void aUserWhoseSessionHasNoOrganizationSeesOnlyUnownedRecords() {
        as(new AuthPrincipal("u1", "CUSTOMER"));

        assertThat(TenantAccess.canSee("org-a")).isFalse();
        assertThat(TenantAccess.canSee(null)).isTrue();
        assertThat(TenantAccess.jpqlClause("p")).isEqualTo(" AND p.tenantId IS NULL");
    }

    @Test
    void workWithNoCallerSuchAsAScheduledJobIsNotLimited() {
        assertThat(TenantAccess.canSee("org-b")).isTrue();
        assertThat(TenantAccess.jpqlClause("p")).isEmpty();
    }
}
