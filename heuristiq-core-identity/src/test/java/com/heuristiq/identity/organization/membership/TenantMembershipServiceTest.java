package com.heuristiq.identity.organization.membership;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

import com.heuristiq.identity.common.UamException;
import com.heuristiq.identity.enums.UamEnums.TenantStatus;
import com.heuristiq.identity.organization.PlatformOrganizationProperties;
import com.heuristiq.identity.organization.Tenant;
import com.heuristiq.identity.organization.membership.repository.TenantMembershipRepository;
import com.heuristiq.identity.organization.repository.TenantRepository;
import com.heuristiq.identity.users.UamUser;
import com.heuristiq.identity.users.repository.UamUserRepository;
import com.heuristiq.identity.users.repository.UamUserSearchCriteria;

/** One account, several organizations: adding members, the member limit, and which organization a sign-in works in. */
class TenantMembershipServiceTest {
    private static final String DEFAULT_ORG = "default";
    private static final String ACME = "acme";

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-14T10:00:00Z"), ZoneOffset.UTC);
    private TenantRepository tenants;
    private UamUserRepository users;
    private InMemoryMemberships memberships;
    private TenantMembershipService service;

    @BeforeEach
    void setUp() {
        tenants = mock(TenantRepository.class);
        users = mock(UamUserRepository.class);
        memberships = new InMemoryMemberships();
        service = new TenantMembershipService(memberships, tenants, users, PlatformOrganizationProperties.defaults(), clock);
        organization(DEFAULT_ORG, PlatformOrganizationProperties.defaults().code(), TenantStatus.ACTIVE, null);
        organization(ACME, "ACME", TenantStatus.ACTIVE, null);
        when(users.findById("u1")).thenReturn(Optional.of(user("u1", null)));
    }

    @Test
    void anAccountWithNoMembershipIsGivenOneInTheDefaultOrganizationAtSignIn() {
        assertThat(service.resolveForLogin(user("u1", null), null)).isEqualTo(DEFAULT_ORG);

        assertThat(memberships.rows).hasSize(1);
        assertThat(memberships.rows.get(0).getTenantId()).isEqualTo(DEFAULT_ORG);
        assertThat(memberships.rows.get(0).getAddedBy()).isEqualTo(TenancyConstants.SYSTEM_ACTOR);
    }

    @Test
    void onlyActiveMembersOfTheOrganizationAreReportedAsMembers() {
        when(users.findById("u2")).thenReturn(Optional.of(user("u2", null)));
        service.addMember(add(ACME, "u1"));
        service.addMember(add(DEFAULT_ORG, "u2"));

        assertThat(service.activeMembersAmong(ACME, java.util.List.of("u1", "u2", "nobody"))).containsExactly("u1");
        assertThat(service.activeMembersAmong(DEFAULT_ORG, java.util.List.of("u1", "u2"))).containsExactly("u2");
        assertThat(service.activeMembersAmong(null, java.util.List.of("u1"))).isEmpty();
    }

    @Test
    void anAccountThatNamesItsOwnOrganizationGetsAMembershipThere() {
        assertThat(service.resolveForLogin(user("u1", ACME), null)).isEqualTo(ACME);
    }

    @Test
    void aPersonInSeveralOrganizationsWorksInTheOneTheyAskFor() {
        service.addMember(add(DEFAULT_ORG, "u1"));
        service.addMember(add(ACME, "u1"));

        assertThat(service.resolveForLogin(user("u1", null), "acme")).isEqualTo(ACME);
        assertThat(service.resolveForLogin(user("u1", null), ACME)).isEqualTo(ACME);
        assertThat(service.resolveForLogin(user("u1", null), null)).isEqualTo(DEFAULT_ORG);
    }

    @Test
    void anOrganizationTheAccountDoesNotBelongToIsRefused() {
        service.addMember(add(DEFAULT_ORG, "u1"));

        assertThatThrownBy(() -> service.resolveForLogin(user("u1", null), "ACME"))
                .isInstanceOfSatisfying(UamException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> service.resolveForLogin(user("u1", null), "no-such-org"))
                .isInstanceOfSatisfying(UamException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void aPausedOrRemovedMemberCannotWorkInTheOrganization() {
        service.addMember(add(DEFAULT_ORG, "u1"));
        service.addMember(add(ACME, "u1"));
        service.updateMember(update(ACME, "u1", TenantMembershipStatus.SUSPENDED));

        assertThatThrownBy(() -> service.resolveForLogin(user("u1", null), "ACME")).isInstanceOf(UamException.class);
        assertThat(service.resolveForLogin(user("u1", null), null)).isEqualTo(DEFAULT_ORG);
    }

    @Test
    void anAccountWithNoActiveOrganizationCannotSignIn() {
        service.addMember(add(DEFAULT_ORG, "u1"));
        service.updateMember(update(DEFAULT_ORG, "u1", TenantMembershipStatus.REMOVED));

        assertThatThrownBy(() -> service.resolveForLogin(user("u1", null), null)).isInstanceOf(UamException.class);
    }

    @Test
    void anOrganizationThatIsNotActiveCannotBeWorkedIn() {
        service.addMember(add(ACME, "u1"));
        organization(ACME, "ACME", TenantStatus.SUSPENDED, null);

        assertThatThrownBy(() -> service.resolveForLogin(user("u1", ACME), "ACME")).isInstanceOf(UamException.class);
    }

    @Test
    void addingTheSamePersonAgainChangesNothingAndABackedOutMemberComesBack() {
        TenantMembership first = service.addMember(add(ACME, "u1"));
        assertThat(service.addMember(add(ACME, "u1")).getId()).isEqualTo(first.getId());

        service.updateMember(update(ACME, "u1", TenantMembershipStatus.REMOVED));
        TenantMembership back = service.addMember(add(ACME, "u1"));

        assertThat(back.getId()).isEqualTo(first.getId());
        assertThat(back.getStatus()).isEqualTo(TenantMembershipStatus.ACTIVE);
        assertThat(memberships.rows).hasSize(1);
    }

    @Test
    void anOrganizationCannotHaveMoreActiveMembersThanItsLimit() {
        organization(ACME, "ACME", TenantStatus.ACTIVE, 1);
        when(users.findById("u2")).thenReturn(Optional.of(user("u2", null)));
        service.addMember(add(ACME, "u1"));

        assertThatThrownBy(() -> service.addMember(add(ACME, "u2"))).isInstanceOf(UamException.class).hasMessageContaining("limit of 1");
    }

    @Test
    void anUnknownAccountCannotBeAdded() {
        when(users.findById("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addMember(add(ACME, "ghost"))).isInstanceOf(UamException.class);
        assertThat(memberships.rows).isEmpty();
    }

    @Test
    void theOrganizationsOfAPersonLeaveOutRemovedOnes() {
        service.addMember(add(DEFAULT_ORG, "u1"));
        service.addMember(add(ACME, "u1"));
        service.updateMember(update(ACME, "u1", TenantMembershipStatus.REMOVED));

        assertThat(service.organizationsOf("u1")).extracting(OrganizationView::tenantId).containsExactly(DEFAULT_ORG);
    }

    @Test
    void aPlatformAdministratorCanOpenAnyActiveOrganizationWithoutBeingAMember() {
        UamUser admin = UamUser.builder().id("root").userName("root").roles(List.of("SUPER_ADMIN")).build();
        when(users.findById("root")).thenReturn(Optional.of(admin));
        Tenant defaultTenant = tenants.findById(DEFAULT_ORG).orElseThrow();
        Tenant acme = tenants.findById(ACME).orElseThrow();
        when(tenants.findAll(any(Sort.class))).thenReturn(List.of(defaultTenant, acme));
        service.addMember(add(DEFAULT_ORG, "root"));

        assertThat(service.resolveForLogin(admin, "ACME")).isEqualTo(ACME);
        assertThat(service.organizationsOf("root")).extracting(OrganizationView::tenantId).containsExactly(DEFAULT_ORG, ACME);
        organization(ACME, "ACME", TenantStatus.SUSPENDED, null);
        assertThatThrownBy(() -> service.resolveForLogin(admin, "ACME")).isInstanceOf(UamException.class);
    }

    @Test
    void searchingMembersFindsOnlyActiveMembersOfThisOrganization() {
        when(users.findById("u2")).thenReturn(Optional.of(user("u2", null)));
        when(users.search(any(UamUserSearchCriteria.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(user("u1", null), user("u2", null))));
        service.addMember(add(ACME, "u1"));
        service.addMember(add(DEFAULT_ORG, "u2"));

        Page<MemberView> found = service.listMembers(new TenantMembershipServiceUseCase.ListInput(ACME, Pageable.unpaged(), "u"));

        assertThat(found.getContent()).extracting(m -> m.membership().getUserId()).containsExactly("u1");
    }

    @Test
    void backfillGivesEveryAccountWithoutAMembershipOneAndLeavesTheRestAlone() {
        when(users.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(user("u1", null), user("u2", ACME), user("u3", null))));
        when(users.findById("u3")).thenReturn(Optional.of(user("u3", null)));
        service.addMember(add(DEFAULT_ORG, "u3"));

        int created = service.backfillMemberships();

        assertThat(created).isEqualTo(2);
        assertThat(memberships.rows).extracting(m -> m.getUserId() + "@" + m.getTenantId())
                .containsExactlyInAnyOrder("u3@" + DEFAULT_ORG, "u1@" + DEFAULT_ORG, "u2@" + ACME);
    }

    @Test
    void aFreshAccountGetsOneMembershipAndAnAccountThatAlreadyBelongsSomewhereIsLeftAlone() {
        service.ensureAnyMembership(user("u1", null));
        service.ensureAnyMembership(user("u2", ACME));
        when(users.findById("u3")).thenReturn(Optional.of(user("u3", null)));
        service.addMember(add(ACME, "u3"));
        service.ensureAnyMembership(user("u3", null));

        assertThat(memberships.rows).extracting(m -> m.getUserId() + "@" + m.getTenantId())
                .containsExactlyInAnyOrder("u1@" + DEFAULT_ORG, "u2@" + ACME, "u3@" + ACME);
    }

    private TenantMembershipServiceUseCase.AddInput add(String tenantId, String userId) {
        return new TenantMembershipServiceUseCase.AddInput(tenantId, new AddMemberRequest(userId), "admin-1");
    }

    private TenantMembershipServiceUseCase.UpdateInput update(String tenantId, String userId, TenantMembershipStatus status) {
        return new TenantMembershipServiceUseCase.UpdateInput(tenantId, userId, new UpdateMemberRequest(status), "admin-1");
    }

    @Test
    void backfillGivesACustomerOrDriverWithNoOrganizationTheOneItJoinedButLeavesStaffShared() {
        UamUser customer = UamUser.builder().id("c1").userName("c1").roles(java.util.List.of("CUSTOMER")).build();
        UamUser driver = UamUser.builder().id("d1").userName("d1").roles(java.util.List.of("DRIVER")).build();
        UamUser staff = UamUser.builder().id("s1").userName("s1").roles(java.util.List.of("SUPER_ADMIN")).build();
        when(users.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(customer, driver, staff)));

        service.backfillMemberships();

        assertThat(customer.getTenantId()).isEqualTo(DEFAULT_ORG);
        assertThat(driver.getTenantId()).isEqualTo(DEFAULT_ORG);
        assertThat(staff.getTenantId()).as("platform staff stay shared").isNull();
        assertThat(memberships.rows).hasSize(3);
    }

    @Test
    void ensureAnyMembershipReportsTheOrganizationTheAccountBelongsTo() {
        assertThat(service.ensureAnyMembership(user("u9", null))).isEqualTo(DEFAULT_ORG);
        assertThat(service.ensureAnyMembership(user("u9", null))).isEqualTo(DEFAULT_ORG);
        assertThat(memberships.rows).hasSize(1);
    }

    private UamUser user(String id, String tenantId) {
        return UamUser.builder().id(id).userName(id).tenantId(tenantId).build();
    }

    private void organization(String id, String code, TenantStatus status, Integer maxUsers) {
        Tenant tenant = Tenant.builder().id(id).code(code).name(code).status(status).maxUsers(maxUsers).build();
        when(tenants.findById(id)).thenReturn(Optional.of(tenant));
        when(tenants.findByCodeIgnoreCase(code)).thenReturn(Optional.of(tenant));
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
