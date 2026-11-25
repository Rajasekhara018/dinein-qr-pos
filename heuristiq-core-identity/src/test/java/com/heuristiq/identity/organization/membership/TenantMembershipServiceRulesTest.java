package com.heuristiq.identity.organization.membership;

import com.heuristiq.identity.enums.UamEnums;
import com.heuristiq.identity.common.UamException;
import com.heuristiq.identity.enums.UamEnums.TenantStatus;
import com.heuristiq.identity.organization.PlatformOrganizationProperties;
import com.heuristiq.identity.organization.Tenant;
import com.heuristiq.identity.organization.membership.repository.TenantMembershipRepository;
import com.heuristiq.identity.organization.repository.TenantRepository;
import com.heuristiq.identity.security.TenantContext;
import com.heuristiq.identity.users.UamUser;
import com.heuristiq.identity.users.repository.UamUserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Member limits, status changes, listing and sign-in organization choice, driven with mocked ports. */
class TenantMembershipServiceRulesTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-14T10:00:00Z"), ZoneOffset.UTC);
    private TenantMembershipRepository memberships;
    private TenantRepository tenants;
    private UamUserRepository users;
    private TenantMembershipService service;

    @BeforeEach
    void setUp() {
        memberships = mock(TenantMembershipRepository.class);
        tenants = mock(TenantRepository.class);
        users = mock(UamUserRepository.class);
        when(memberships.save(any(TenantMembership.class))).thenAnswer(inv -> inv.getArgument(0));
        service = new TenantMembershipService(memberships, tenants, users, PlatformOrganizationProperties.defaults(), clock);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    private Tenant org(String id, TenantStatus status, Integer maxUsers) {
        Tenant tenant = Tenant.builder().id(id).code(id.toUpperCase()).name(id).status(status).maxUsers(maxUsers).timeZone("UTC").build();
        when(tenants.findById(id)).thenReturn(Optional.of(tenant));
        when(tenants.findByCodeIgnoreCase(id.toUpperCase())).thenReturn(Optional.of(tenant));
        when(tenants.findByCodeIgnoreCase(id)).thenReturn(Optional.of(tenant));
        return tenant;
    }

    private static TenantMembership member(String tenant, String user, TenantMembershipStatus status) {
        return TenantMembership.builder().id(tenant + ":" + user).tenantId(tenant).userId(user).status(status)
                .joinedAt(LocalDateTime.parse("2026-01-01T00:00:00")).build();
    }

    @Test
    void listingShowsEachMemberWithTheirAccountDetailsAndToleratesMissingAccounts() {
        org("acme", TenantStatus.ACTIVE, null);
        TenantMembership a = member("acme", "u1", TenantMembershipStatus.ACTIVE);
        TenantMembership b = member("acme", "ghost", TenantMembershipStatus.ACTIVE);
        Pageable pageable = PageRequest.of(0, 10);
        when(memberships.findByTenantId("acme", pageable)).thenReturn(new PageImpl<>(List.of(a, b), pageable, 2));
        when(users.findAllByIds(any())).thenReturn(List.of(
                UamUser.builder().id("u1").userName("jane").email("j@x.com").displayName("Jane").employeeCode("E1")
                        .roles(List.of("DRIVER")).build()));

        Page<MemberView> page = service.listMembers(
                new TenantMembershipServiceUseCase.ListInput("acme", pageable, " "));

        assertThat(page.getContent()).hasSize(2);
        var first = page.getContent().get(0);
        assertThat(first.userName()).isEqualTo("jane");
        assertThat(first.email()).isEqualTo("j@x.com");
        assertThat(first.displayName()).isEqualTo("Jane");
        assertThat(first.employeeCode()).isEqualTo("E1");
        assertThat(first.userType()).isEqualTo(UamEnums.UserType.DRIVER);
        var second = page.getContent().get(1);
        assertThat(second.userName()).isNull();
        assertThat(second.email()).isNull();
        assertThat(second.userType()).isNull();
    }

    @Test
    void listingAnotherOrganizationIsRefusedForANonSuperAdmin() {
        org("acme", TenantStatus.ACTIVE, null);
        TenantContext.set("other-org");
        assertThatThrownBy(() -> service.listMembers(new TenantMembershipServiceUseCase.ListInput("acme", Pageable.unpaged(), null)))
                .isInstanceOf(UamException.class);
        verify(memberships, never()).findByTenantId(any(), any());
    }

    @Test
    void listingNeedsAKnownOrganization() {
        when(tenants.findById("nope")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.listMembers(new TenantMembershipServiceUseCase.ListInput("nope", Pageable.unpaged(), null)))
                .isInstanceOfSatisfying(UamException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> service.listMembers(new TenantMembershipServiceUseCase.ListInput(" ", Pageable.unpaged(), null)))
                .hasMessage("Organization id is required");
    }

    @Test
    void anArchivedOrganizationTakesNoNewMembers() {
        org("old", TenantStatus.ARCHIVED, null);
        assertThatThrownBy(() -> service.addMember(new TenantMembershipServiceUseCase.AddInput("old", new AddMemberRequest("u1"), "admin")))
                .hasMessageContaining("archived");
    }

    @Test
    void addingNeedsAUserId() {
        org("acme", TenantStatus.ACTIVE, null);
        assertThatThrownBy(() -> service.addMember(new TenantMembershipServiceUseCase.AddInput("acme", new AddMemberRequest(" "), "admin")))
                .hasMessage("User id is required");
    }

    @Test
    void addingANewMemberRecordsWhoAddedThemAndWhen() {
        org("acme", TenantStatus.ACTIVE, null);
        when(users.findById("u1")).thenReturn(Optional.of(UamUser.builder().id("u1").build()));
        when(memberships.findByTenantIdAndUserId("acme", "u1")).thenReturn(Optional.empty());

        TenantMembership added = service.addMember(new TenantMembershipServiceUseCase.AddInput("acme", new AddMemberRequest(" u1 "), "admin"));

        assertThat(added.getStatus()).isEqualTo(TenantMembershipStatus.ACTIVE);
        assertThat(added.getAddedBy()).isEqualTo("admin");
        assertThat(added.getJoinedAt()).isEqualTo(LocalDateTime.parse("2026-10-14T10:00:00"));
    }

    @Test
    void updatingAMemberWithAFullOrganizationIsRefusedOnlyWhenActivating() {
        org("acme", TenantStatus.ACTIVE, 2);
        TenantMembership suspended = member("acme", "u1", TenantMembershipStatus.SUSPENDED);
        when(memberships.findByTenantIdAndUserId("acme", "u1")).thenReturn(Optional.of(suspended));
        when(memberships.countByTenantIdAndStatus("acme", TenantMembershipStatus.ACTIVE)).thenReturn(2L);

        assertThatThrownBy(() -> service.updateMember(new TenantMembershipServiceUseCase.UpdateInput(
                "acme", "u1", new UpdateMemberRequest(TenantMembershipStatus.ACTIVE), "admin")))
                .isInstanceOfSatisfying(UamException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining("limit of 2 members");

        TenantMembership removed = service.updateMember(new TenantMembershipServiceUseCase.UpdateInput(
                "acme", "u1", new UpdateMemberRequest(TenantMembershipStatus.REMOVED), "admin"));
        assertThat(removed.getStatus()).isEqualTo(TenantMembershipStatus.REMOVED);
        assertThat(removed.getUpdatedBy()).isEqualTo("admin");
    }

    @Test
    void reactivatingWithRoomWorksAndUnchangedStatusSavesNothing() {
        org("acme", TenantStatus.ACTIVE, 5);
        TenantMembership suspended = member("acme", "u1", TenantMembershipStatus.SUSPENDED);
        when(memberships.findByTenantIdAndUserId("acme", "u1")).thenReturn(Optional.of(suspended));
        when(memberships.countByTenantIdAndStatus("acme", TenantMembershipStatus.ACTIVE)).thenReturn(1L);

        assertThat(service.updateMember(new TenantMembershipServiceUseCase.UpdateInput(
                "acme", "u1", new UpdateMemberRequest(TenantMembershipStatus.ACTIVE), "admin")).getStatus())
                .isEqualTo(TenantMembershipStatus.ACTIVE);
        verify(memberships).save(suspended);

        service.updateMember(new TenantMembershipServiceUseCase.UpdateInput(
                "acme", "u1", new UpdateMemberRequest(TenantMembershipStatus.ACTIVE), "admin"));
        verify(memberships, org.mockito.Mockito.times(1)).save(any(TenantMembership.class));
    }

    @Test
    void updatingAPersonWhoIsNotAMemberFails() {
        org("acme", TenantStatus.ACTIVE, null);
        when(memberships.findByTenantIdAndUserId("acme", "x")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.updateMember(new TenantMembershipServiceUseCase.UpdateInput(
                "acme", "x", new UpdateMemberRequest(TenantMembershipStatus.ACTIVE), "admin")))
                .hasMessageContaining("not a member");
    }

    @Test
    void aZeroOrNegativeMemberLimitMeansNoLimit() {
        org("acme", TenantStatus.ACTIVE, 0);
        when(users.findById("u1")).thenReturn(Optional.of(UamUser.builder().id("u1").build()));
        when(memberships.findByTenantIdAndUserId("acme", "u1")).thenReturn(Optional.empty());
        assertThat(service.addMember(new TenantMembershipServiceUseCase.AddInput("acme", new AddMemberRequest("u1"), "a")).getStatus())
                .isEqualTo(TenantMembershipStatus.ACTIVE);
        verify(memberships, never()).countByTenantIdAndStatus(any(), any());
    }

    @Test
    void activeMembersAmongIgnoresBlankInputNullsDuplicatesAndInactiveMembers() {
        when(memberships.findByTenantIdAndUserId("acme", "u1")).thenReturn(Optional.of(member("acme", "u1", TenantMembershipStatus.ACTIVE)));
        when(memberships.findByTenantIdAndUserId("acme", "u2")).thenReturn(Optional.of(member("acme", "u2", TenantMembershipStatus.SUSPENDED)));
        when(memberships.findByTenantIdAndUserId("acme", "u3")).thenReturn(Optional.empty());

        assertThat(service.activeMembersAmong("acme", java.util.Arrays.asList("u1", "u1", null, "u2", "u3"))).containsExactly("u1");
        assertThat(service.activeMembersAmong(" ", List.of("u1"))).isEmpty();
        assertThat(service.activeMembersAmong(null, List.of("u1"))).isEmpty();
        assertThat(service.activeMembersAmong("acme", null)).isEmpty();
    }

    @Test
    void ensureAnyMembershipReusesTheEarliestOrCreatesOneOrReturnsNull() {
        TenantMembership early = member("acme", "u1", TenantMembershipStatus.ACTIVE);
        TenantMembership late = TenantMembership.builder().tenantId("beta").userId("u1").status(TenantMembershipStatus.ACTIVE)
                .joinedAt(LocalDateTime.parse("2027-01-01T00:00:00")).build();
        TenantMembership undated = TenantMembership.builder().tenantId("zeta").userId("u1").status(TenantMembershipStatus.ACTIVE).build();
        when(memberships.findByUserId("u1")).thenReturn(List.of(undated, late, early));
        assertThat(service.ensureAnyMembership(UamUser.builder().id("u1").build())).isEqualTo("acme");

        when(memberships.findByUserId("u2")).thenReturn(List.of());
        assertThat(service.ensureAnyMembership(UamUser.builder().id("u2").build())).isNull();

        org("default", TenantStatus.ACTIVE, null);
        when(tenants.findByCodeIgnoreCase(PlatformOrganizationProperties.defaults().code()))
                .thenReturn(Optional.of(Tenant.builder().id("default").code(PlatformOrganizationProperties.defaults().code()).build()));
        assertThat(service.ensureAnyMembership(UamUser.builder().id("u2").build())).isEqualTo("default");
    }

    @Test
    void signInPrefersTheAccountsOwnOrganizationThenTheDefaultThenTheFirstJoined() {
        org("acme", TenantStatus.ACTIVE, null);
        org("beta", TenantStatus.ACTIVE, null);
        Tenant platform = Tenant.builder().id("platform").code(PlatformOrganizationProperties.defaults().code()).status(TenantStatus.ACTIVE).build();
        when(tenants.findById("platform")).thenReturn(Optional.of(platform));
        when(tenants.findByCodeIgnoreCase(PlatformOrganizationProperties.defaults().code())).thenReturn(Optional.of(platform));
        TenantMembership acme = member("acme", "u1", TenantMembershipStatus.ACTIVE);
        TenantMembership beta = TenantMembership.builder().tenantId("beta").userId("u1").status(TenantMembershipStatus.ACTIVE)
                .joinedAt(LocalDateTime.parse("2026-06-01T00:00:00")).build();
        TenantMembership plat = TenantMembership.builder().tenantId("platform").userId("u1").status(TenantMembershipStatus.ACTIVE)
                .joinedAt(LocalDateTime.parse("2026-09-01T00:00:00")).build();
        when(memberships.findByUserId("u1")).thenReturn(List.of(acme, beta, plat));

        assertThat(service.resolveForLogin(UamUser.builder().id("u1").tenantId("beta").build(), null)).isEqualTo("beta");
        assertThat(service.resolveForLogin(UamUser.builder().id("u1").tenantId("gone").build(), null)).isEqualTo("platform");

        when(memberships.findByUserId("u1")).thenReturn(List.of(beta, acme));
        assertThat(service.resolveForLogin(UamUser.builder().id("u1").build(), null)).isEqualTo("acme");
    }

    @Test
    void signInWithAnUnknownOrForeignRequestedOrganizationIsForbidden() {
        org("acme", TenantStatus.ACTIVE, null);
        org("beta", TenantStatus.ACTIVE, null);
        when(memberships.findByUserId("u1")).thenReturn(List.of(member("acme", "u1", TenantMembershipStatus.ACTIVE)));
        when(tenants.findById("zzz")).thenReturn(Optional.empty());
        when(tenants.findByCodeIgnoreCase("zzz")).thenReturn(Optional.empty());
        UamUser user = UamUser.builder().id("u1").build();

        assertThatThrownBy(() -> service.resolveForLogin(user, "zzz"))
                .isInstanceOfSatisfying(UamException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> service.resolveForLogin(user, " beta "))
                .isInstanceOfSatisfying(UamException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThat(service.resolveForLogin(user, "ACME")).isEqualTo("acme");
    }

    @Test
    void aSuperAdminMayOpenAnyWorkableOrganization() {
        org("acme", TenantStatus.ACTIVE, null);
        org("beta", TenantStatus.ACTIVE, null);
        org("frozen", TenantStatus.ARCHIVED, null);
        when(memberships.findByUserId("root")).thenReturn(List.of(member("acme", "root", TenantMembershipStatus.ACTIVE)));
        UamUser root = UamUser.builder().id("root").roles(List.of("super_admin")).build();

        assertThat(service.resolveForLogin(root, "beta")).isEqualTo("beta");
        assertThatThrownBy(() -> service.resolveForLogin(root, "frozen")).isInstanceOf(UamException.class);
    }

    @Test
    void signInFailsWhenNoMembershipIsActiveAndFallsBackToTheAccountsTenantWhenNoneCanBeMade() {
        org("acme", TenantStatus.ACTIVE, null);
        when(memberships.findByUserId("u1")).thenReturn(List.of(member("acme", "u1", TenantMembershipStatus.SUSPENDED)));
        assertThatThrownBy(() -> service.resolveForLogin(UamUser.builder().id("u1").build(), null))
                .hasMessageContaining("do not belong to any active organization");

        when(memberships.findByUserId("u9")).thenReturn(List.of());
        when(tenants.findByCodeIgnoreCase(PlatformOrganizationProperties.defaults().code())).thenReturn(Optional.empty());
        assertThat(service.resolveForLogin(UamUser.builder().id("u9").tenantId("legacy").build(), null)).isEqualTo("legacy");
    }

    @Test
    void organizationsOfListsNonRemovedMembershipsAndSkipsUnknownOrganizations() {
        org("acme", TenantStatus.ACTIVE, null);
        when(users.findById("u1")).thenReturn(Optional.of(UamUser.builder().id("u1").roles(List.of("OPS")).build()));
        when(tenants.findById("gone")).thenReturn(Optional.empty());
        when(memberships.findByUserId("u1")).thenReturn(List.of(
                member("acme", "u1", TenantMembershipStatus.SUSPENDED),
                member("gone", "u1", TenantMembershipStatus.ACTIVE),
                member("acme2", "u1", TenantMembershipStatus.REMOVED)));

        List<OrganizationView> views = service.organizationsOf("u1");

        assertThat(views).hasSize(1);
        assertThat(views.get(0).tenantId()).isEqualTo("acme");
        assertThat(views.get(0).status()).isEqualTo(TenantMembershipStatus.SUSPENDED);
    }

    @Test
    void aSuperAdminSeesEveryActiveOrganizationButNotArchivedOnes() {
        Tenant active = Tenant.builder().id("a").code("A").name("A").status(TenantStatus.ACTIVE).build();
        Tenant unset = Tenant.builder().id("b").code("B").name("B").status(null).build();
        Tenant archived = Tenant.builder().id("c").code("C").name("C").status(TenantStatus.ARCHIVED).build();
        when(users.findById("root")).thenReturn(Optional.of(UamUser.builder().id("root").roles(List.of("SUPER_ADMIN")).build()));
        when(tenants.findAll(any(org.springframework.data.domain.Sort.class))).thenReturn(List.of(active, unset, archived));

        assertThat(service.organizationsOf("root")).extracting(OrganizationView::tenantId)
                .containsExactly("a", "b");
    }

    @Test
    void anUnknownAccountHasNoOrganizations() {
        when(users.findById("ghost")).thenReturn(Optional.empty());
        when(memberships.findByUserId("ghost")).thenReturn(List.of());
        assertThat(service.organizationsOf("ghost")).isEmpty();
    }
}
