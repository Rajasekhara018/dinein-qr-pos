package com.heuristiq.identity.pos.adapter.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.heuristiq.identity.organization.Tenant;
import com.heuristiq.identity.organization.membership.TenantMembership;
import com.heuristiq.identity.organization.membership.TenantMembershipStatus;
import com.heuristiq.identity.organization.membership.repository.TenantMembershipRepository;
import com.heuristiq.identity.organization.repository.TenantRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TenantPosOrganizationDirectoryTest {

    private final TenantRepository tenants = mock(TenantRepository.class);
    private final TenantMembershipRepository members = mock(TenantMembershipRepository.class);
    private final TenantPosOrganizationDirectory directory = new TenantPosOrganizationDirectory(tenants, members);

    @Test
    void suspendedMemberIsNotActive() {
        when(members.findByTenantIdAndUserId("t1", "u1")).thenReturn(Optional.of(
                TenantMembership.builder().tenantId("t1").userId("u1").status(TenantMembershipStatus.SUSPENDED).build()));
        when(members.findByTenantIdAndUserId("t1", "u2")).thenReturn(Optional.of(
                TenantMembership.builder().tenantId("t1").userId("u2").status(TenantMembershipStatus.ACTIVE).build()));
        assertThat(directory.isActiveMember("t1", "u1")).isFalse();
        assertThat(directory.isActiveMember("t1", "u2")).isTrue();
        assertThat(directory.isActiveMember("t1", "nobody")).isFalse();
    }

    @Test
    void unknownTenantDoesNotExist() {
        when(tenants.findById("t1")).thenReturn(Optional.of(new Tenant()));
        assertThat(directory.tenantExists("t1")).isTrue();
        assertThat(directory.tenantExists("nope")).isFalse();
    }
}
