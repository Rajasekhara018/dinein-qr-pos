package com.railcab.nms.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TenantScopeTest {

    @Test
    void anOrganizationSeesItsOwnRecordsAndRecordsWithNoOrganizationButNotAnotherOrganizations() {
        TenantScope scope = TenantScope.limitedTo("org-a");

        assertThat(scope.allows("org-a")).isTrue();
        assertThat(scope.allows(null)).isTrue();
        assertThat(scope.allows(" ")).isTrue();
        assertThat(scope.allows("org-b")).isFalse();
    }

    @Test
    void aScopeWithNoOrganizationSeesOnlyRecordsWithNoOrganization() {
        TenantScope scope = TenantScope.limitedTo(null);

        assertThat(scope.hasTenant()).isFalse();
        assertThat(scope.allows(null)).isTrue();
        assertThat(scope.allows("org-a")).isFalse();
        assertThat(scope.tenantIdOrEmpty()).isEmpty();
    }

    @Test
    void anUnrestrictedScopeSeesEverything() {
        assertThat(TenantScope.UNRESTRICTED.allows("org-a")).isTrue();
        assertThat(TenantScope.UNRESTRICTED.allows(null)).isTrue();
    }

    @Test
    void visibleTenantIdsListTheUnownedMarkerAndTheOrganization() {
        assertThat(TenantScope.limitedTo("org-a").visibleTenantIds()).containsExactly(null, "org-a");
        assertThat(TenantScope.limitedTo(null).visibleTenantIds()).containsExactly((String) null);
    }

    @Test
    void aPageQueryWithoutAScopeIsUnrestrictedAndCanBeLimited() {
        PageQuery query = new PageQuery(1, 20);

        assertThat(query.scope()).isEqualTo(TenantScope.UNRESTRICTED);
        PageQuery limited = query.withScope(TenantScope.limitedTo("org-a"));
        assertThat(limited.page()).isEqualTo(1);
        assertThat(limited.size()).isEqualTo(20);
        assertThat(limited.scope().tenantId()).isEqualTo("org-a");
        assertThat(new PageQuery(0, 5, null).scope()).isEqualTo(TenantScope.UNRESTRICTED);
    }
}
