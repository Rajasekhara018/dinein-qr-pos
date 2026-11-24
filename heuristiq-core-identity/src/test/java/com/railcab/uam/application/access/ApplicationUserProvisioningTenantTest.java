package com.heuristiq.uam.application.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import com.heuristiq.uam.common.UamException;
import com.heuristiq.uam.enums.UamEnums;
import com.heuristiq.uam.organization.PlatformOrganizationProperties;
import com.heuristiq.uam.organization.Tenant;
import com.heuristiq.uam.organization.membership.TenantMembership;
import com.heuristiq.uam.organization.membership.TenantMembershipService;
import com.heuristiq.uam.organization.membership.TenantMembershipStatus;
import com.heuristiq.uam.organization.membership.repository.TenantMembershipRepository;
import com.heuristiq.uam.organization.repository.TenantRepository;
import com.heuristiq.uam.organization.subscription.TenantApplicationServiceUseCase;
import com.heuristiq.uam.registration.UserRegistrationServiceUseCase;
import com.heuristiq.uam.usercredentials.UserCredentialServiceUseCase;
import com.heuristiq.uam.users.UamUser;
import com.heuristiq.uam.users.repository.UamUserRepository;

/**
 * Provisioning against the real membership rules (memberships kept in memory): a person onboarded into an organization
 * becomes a member of that organization only, never of the default one or of another organization.
 */
class ApplicationUserProvisioningTenantTest {
    private static final String DEFAULT_ORG = "default";
    private static final String ORG_A = "org-a";
    private static final String ORG_B = "org-b";
    private static final String APP = "PASUPU_KUMKUMA_MATRIMONY_APP";
    private static final String MOBILE = "+919876543210";
    private static final String EMAIL = "asha@example.com";

    private UamUserRepository users;
    private InMemoryMemberships memberships;
    private TenantMembershipService membershipService;
    private ApplicationUserProvisioningService service;
    private UamUser asha;

    @BeforeEach
    void setUp() {
        users = mock(UamUserRepository.class);
        TenantRepository tenants = mock(TenantRepository.class);
        TenantApplicationServiceUseCase subscriptions = mock(TenantApplicationServiceUseCase.class);
        ApplicationAccessServiceUseCase access = mock(ApplicationAccessServiceUseCase.class);
        memberships = new InMemoryMemberships();
        membershipService = new TenantMembershipService(memberships, tenants, users, PlatformOrganizationProperties.defaults());
        service = new ApplicationUserProvisioningService(users, mock(UserRegistrationServiceUseCase.class), access,
                mock(UserCredentialServiceUseCase.class), membershipService, tenants, subscriptions);

        for (String id : List.of(DEFAULT_ORG, ORG_A, ORG_B)) {
            String code = DEFAULT_ORG.equals(id) ? PlatformOrganizationProperties.defaults().code() : id.toUpperCase();
            Tenant tenant = Tenant.builder().id(id).code(code).name(code).status(UamEnums.TenantStatus.ACTIVE).build();
            when(tenants.findById(id)).thenReturn(Optional.of(tenant));
            when(tenants.findByCodeIgnoreCase(code)).thenReturn(Optional.of(tenant));
            when(subscriptions.activeApplicationCodes(id)).thenReturn(List.of(APP));
        }
        asha = UamUser.builder().id("u-1").status(UamEnums.UserStatus.ACTIVE).roles(List.of("CUSTOMER")).build();
        when(users.findByPhone(MOBILE)).thenReturn(Optional.of(asha));
        when(users.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(asha));
        when(users.findById("u-1")).thenReturn(Optional.of(asha));
        when(users.save(any(UamUser.class))).thenAnswer(call -> call.getArgument(0));
        when(access.resolveAccessibleApplications(any())).thenReturn(new ApplicationAccessServiceUseCase.ResolveOutput(List.of(APP)));
    }

    @Test
    void aPersonProvisionedInOrganizationAIsAMemberOfAOnlyAndNotOfBOrTheDefault() {
        service.provision(input(ORG_A));

        assertThat(membershipService.activeMembersAmong(ORG_A, List.of("u-1"))).containsExactly("u-1");
        assertThat(membershipService.activeMembersAmong(ORG_B, List.of("u-1"))).isEmpty();
        assertThat(membershipService.activeMembersAmong(DEFAULT_ORG, List.of("u-1"))).isEmpty();
        assertThat(asha.getTenantId()).isEqualTo(ORG_A);
    }

    @Test
    void anAccountAlreadyInOrganizationAGainsASecondMembershipWhenOnboardedIntoB() {
        service.provision(input(ORG_A));
        service.provision(input(ORG_B));
        // a repeat is idempotent: no duplicate membership
        service.provision(input(ORG_B));

        assertThat(memberships.rows).extracting(TenantMembership::getTenantId).containsExactlyInAnyOrder(ORG_A, ORG_B);
        assertThat(memberships.rows).allMatch(row -> row.getStatus() == TenantMembershipStatus.ACTIVE);
        // the account keeps the organization it first belonged to
        assertThat(asha.getTenantId()).isEqualTo(ORG_A);
    }

    @Test
    void withNoOrganizationNamedThePersonLandsInTheDefaultOrganizationAsBefore() {
        service.provision(input(null));

        assertThat(memberships.rows).extracting(TenantMembership::getTenantId).containsExactly(DEFAULT_ORG);
        assertThat(asha.getTenantId()).isEqualTo(DEFAULT_ORG);
    }

    @Test
    void aRefusedOrganizationLeavesNoMembershipAnywhere() {
        assertThatThrownBy(() -> service.provision(input("org-unknown"))).isInstanceOf(UamException.class);

        assertThat(memberships.rows).isEmpty();
        assertThat(asha.getTenantId()).isNull();
    }

    private ApplicationUserProvisioningServiceUseCase.ProvisionInput input(String tenantId) {
        return new ApplicationUserProvisioningServiceUseCase.ProvisionInput(
                new ProvisionApplicationUserRequest("Asha Rao", MOBILE, EMAIL, UamEnums.UserType.CUSTOMER, APP, tenantId));
    }

    private static final class InMemoryMemberships implements TenantMembershipRepository {
        final List<TenantMembership> rows = new ArrayList<>();

        @Override
        public TenantMembership save(TenantMembership membership) {
            if (membership.getId() == null) {
                membership.setId(UUID.randomUUID().toString());
                rows.add(membership);
            }
            return membership;
        }

        @Override
        public Optional<TenantMembership> findByTenantIdAndUserId(String tenantId, String userId) {
            return rows.stream().filter(r -> r.getTenantId().equals(tenantId) && r.getUserId().equals(userId)).findFirst();
        }

        @Override
        public List<TenantMembership> findByUserId(String userId) {
            return rows.stream().filter(r -> r.getUserId().equals(userId)).toList();
        }

        @Override
        public Page<TenantMembership> findByTenantId(String tenantId, Pageable pageable) {
            return new PageImpl<>(rows.stream().filter(r -> r.getTenantId().equals(tenantId)).toList());
        }

        @Override
        public long countByTenantIdAndStatus(String tenantId, TenantMembershipStatus status) {
            return rows.stream().filter(r -> r.getTenantId().equals(tenantId) && r.getStatus() == status).count();
        }
    }
}
