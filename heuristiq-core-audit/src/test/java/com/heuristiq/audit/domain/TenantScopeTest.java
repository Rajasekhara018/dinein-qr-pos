package com.heuristiq.audit.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TenantScopeTest {

    @Test
    void ownOrganizationAndLegacyRecordsAreVisibleOtherOrganizationsAreNot() {
        TenantScope scope = TenantScope.of("org-a");
        assertThat(scope.allows("org-a")).isTrue();
        assertThat(scope.allows("org-b")).isFalse();
        assertThat(scope.allows(null)).as("legacy record").isTrue();
        assertThat(scope.allows(" ")).isTrue();
    }

    @Test
    void aSessionWithoutOrganizationSeesOnlyLegacyRecords() {
        assertThat(TenantScope.of(null).allows(null)).isTrue();
        assertThat(TenantScope.of("  ").allows("org-a")).isFalse();
        assertThat(TenantScope.of(null).hasTenant()).isFalse();
    }

    @Test
    void unrestrictedSeesEverything() {
        assertThat(TenantScope.ALL.allows("org-a")).isTrue();
        assertThat(TenantScope.ALL.allows("org-b")).isTrue();
        assertThat(TenantScope.ALL.allows(null)).isTrue();
    }

    @Test
    void organizationIsTrimmed() {
        assertThat(TenantScope.of(" org-a ").tenantId()).isEqualTo("org-a");
    }
}
