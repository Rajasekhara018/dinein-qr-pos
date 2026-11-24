package com.heuristiq.uam.cache;

import com.heuristiq.uam.auth.MyPrivilegesService;
import com.heuristiq.uam.auth.MyPrivilegesServiceUseCase.ForUserInput;
import com.heuristiq.uam.common.UamException;
import com.heuristiq.uam.enums.UamEnums.TenantStatus;
import com.heuristiq.uam.organization.Tenant;
import com.heuristiq.uam.organization.membership.OrganizationView;
import com.heuristiq.uam.organization.membership.TenantMembership;
import com.heuristiq.uam.organization.membership.TenantMembershipService;
import com.heuristiq.uam.organization.membership.TenantMembershipStatus;
import com.heuristiq.uam.organization.membership.repository.TenantMembershipRepository;
import com.heuristiq.uam.organization.repository.TenantRepository;
import com.heuristiq.uam.organization.PlatformOrganizationProperties;
import com.heuristiq.uam.organization.subscription.TenantApplication;
import com.heuristiq.uam.organization.subscription.TenantApplicationService;
import com.heuristiq.uam.organization.subscription.TenantApplicationStatus;
import com.heuristiq.uam.organization.subscription.repository.TenantApplicationRepository;
import com.heuristiq.uam.application.repository.ApplicationRepository;
import com.heuristiq.uam.permissions.Permission;
import com.heuristiq.uam.permissions.PermissionRepository;
import com.heuristiq.uam.role.UamRole;
import com.heuristiq.uam.role.repository.UamRoleRepository;
import com.heuristiq.uam.security.TenantContext;
import com.heuristiq.uam.users.UamUser;
import com.heuristiq.uam.users.repository.UamUserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Behaviour of the caches as wired by {@link UamCacheBeanPostProcessor}: hits, tenant/user key isolation, eviction by every
 * wired writer (visible at once on the same instance), negative results not cached, and the disabled pass-through.
 */
class UamCachesWiringTest {

    private UamUserRepository rawUsers;
    private UamRoleRepository rawRoles;
    private PermissionRepository rawPermissions;
    private TenantRepository rawTenants;
    private TenantMembershipRepository rawMemberships;
    private TenantApplicationRepository rawApplications;

    private UamCacheProperties properties;
    private UamCaches caches;
    private UamCacheBeanPostProcessor processor;

    private UamUserRepository users;
    private UamRoleRepository roles;
    private PermissionRepository permissions;
    private TenantRepository tenants;
    private TenantMembershipRepository memberships;
    private TenantApplicationRepository applications;

    private CachingMyPrivilegesService privileges;
    private CachingTenantMembershipService organizations;
    private CachingTenantApplicationService applicationCodes;

    @BeforeEach
    void setUp() {
        rawUsers = mock(UamUserRepository.class);
        rawRoles = mock(UamRoleRepository.class);
        rawPermissions = mock(PermissionRepository.class);
        rawTenants = mock(TenantRepository.class);
        rawMemberships = mock(TenantMembershipRepository.class);
        rawApplications = mock(TenantApplicationRepository.class);
        wire(new UamCacheProperties());
    }

    @SuppressWarnings("unchecked")
    private void wire(UamCacheProperties props) {
        properties = props;
        caches = UamCaches.create(props, new UamCacheTest.FakeTicker(), null);
        org.springframework.beans.factory.ObjectProvider<UamCaches> provider = mock(org.springframework.beans.factory.ObjectProvider.class);
        when(provider.getObject()).thenReturn(caches);
        processor = new UamCacheBeanPostProcessor(provider);
        users = (UamUserRepository) processor.postProcessAfterInitialization(rawUsers, "users");
        roles = (UamRoleRepository) processor.postProcessAfterInitialization(rawRoles, "roles");
        permissions = (PermissionRepository) processor.postProcessAfterInitialization(rawPermissions, "permissions");
        tenants = (TenantRepository) processor.postProcessAfterInitialization(rawTenants, "tenants");
        memberships = (TenantMembershipRepository) processor.postProcessAfterInitialization(rawMemberships, "memberships");
        applications = (TenantApplicationRepository) processor.postProcessAfterInitialization(rawApplications, "applications");

        privileges = new CachingMyPrivilegesService(new MyPrivilegesService(users, roles, permissions), caches);
        organizations = new CachingTenantMembershipService(new TenantMembershipService(memberships, tenants, users,
                PlatformOrganizationProperties.defaults()), caches);
        applicationCodes = new CachingTenantApplicationService(new TenantApplicationService(tenants, mock(ApplicationRepository.class),
                applications), caches);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    // ---- fixtures ----

    private static UamUser user(String id, String... roles) {
        UamUser user = new UamUser();
        user.setId(id);
        user.setRoles(List.of(roles));
        return user;
    }

    private static UamRole role(String code, String... permissions) {
        UamRole role = new UamRole();
        role.setCode(code);
        role.setPermissions(List.of(permissions));
        return role;
    }

    private static Permission permission(String code, int privilegeId) {
        return Permission.builder().code(code).attributes(Map.of("privilegeId", privilegeId)).build();
    }

    private void givenRole(String code, String... permissionCodes) {
        when(rawRoles.findByCodeIgnoreCase(code)).thenReturn(Optional.of(role(code, permissionCodes)));
        org.mockito.Mockito.doAnswer(call -> ((java.util.Collection<String>) call.getArgument(0)).stream()
                .map(c -> permission(c, 1)).toList()).when(rawPermissions).findAllByCodeIn(any());
    }

    private List<String> permissionsOf(String userId) {
        return privileges.forUser(new ForUserInput(userId)).privileges().permissions();
    }

    // ---- my privileges ----

    @Test
    void sessionsOfOneUserHoldingDifferentRolesNeverShareAnEntry() {
        // A franchise role left out of a later token (franchise closed) must not be served from the earlier session's entry.
        when(rawUsers.findById("u1")).thenReturn(Optional.of(user("u1", "MANAGER", "FRANCHISE_OWNER")));
        givenRole("MANAGER", "A_READ");
        givenRole("FRANCHISE_OWNER", "F_READ");

        assertThat(privileges.forUser(new ForUserInput("u1", List.of("ROLE_MANAGER", "ROLE_FRANCHISE_OWNER"))).privileges().permissions())
                .containsExactlyInAnyOrder("A_READ", "F_READ");
        assertThat(privileges.forUser(new ForUserInput("u1", List.of("ROLE_MANAGER"))).privileges().permissions())
                .containsExactly("A_READ");
        assertThat(privileges.forUser(new ForUserInput("u1", List.of("role_manager"))).privileges().permissions())
                .as("the same roles in another case share the entry").containsExactly("A_READ");

        verify(rawUsers, times(2)).findById("u1");
    }

    @Test
    void privilegesAreServedFromTheCacheOnTheSecondCall() {
        when(rawUsers.findById("u1")).thenReturn(Optional.of(user("u1", "MANAGER")));
        givenRole("MANAGER", "A_READ");

        assertThat(permissionsOf("u1")).containsExactly("A_READ");
        assertThat(permissionsOf("u1")).containsExactly("A_READ");

        verify(rawUsers, times(1)).findById("u1");
    }

    @Test
    void aRoleChangeIsVisibleImmediatelyOnTheSameInstance() {
        when(rawUsers.findById("u1")).thenReturn(Optional.of(user("u1", "MANAGER")));
        givenRole("MANAGER", "A_READ");
        assertThat(permissionsOf("u1")).containsExactly("A_READ");

        UamRole changed = role("MANAGER", "B_READ");
        when(rawRoles.findByCodeIgnoreCase("MANAGER")).thenReturn(Optional.of(changed));
        assertThat(permissionsOf("u1")).as("not yet evicted").containsExactly("A_READ");

        roles.save(changed);

        assertThat(permissionsOf("u1")).containsExactly("B_READ");
    }

    @Test
    void aRoleDeleteAndAPermissionChangeEvictToo() {
        when(rawUsers.findById("u1")).thenReturn(Optional.of(user("u1", "MANAGER")));
        givenRole("MANAGER", "A_READ");
        permissionsOf("u1");
        roles.delete(role("MANAGER"));
        permissionsOf("u1");
        permissions.save(permission("A_READ", 1));
        permissionsOf("u1");
        permissions.delete(permission("A_READ", 1));
        permissionsOf("u1");

        verify(rawUsers, times(4)).findById("u1");
    }

    @Test
    void aUserRoleAssignmentIsVisibleAtOnceAndOnlyForThatUser() {
        UamUser u1 = user("u1", "MANAGER");
        when(rawUsers.findById("u1")).thenReturn(Optional.of(u1));
        when(rawUsers.findById("u2")).thenReturn(Optional.of(user("u2", "MANAGER")));
        when(rawRoles.findByCodeIgnoreCase("MANAGER")).thenReturn(Optional.of(role("MANAGER", "A_READ")));
        when(rawRoles.findByCodeIgnoreCase("ADMIN")).thenReturn(Optional.of(role("ADMIN", "Z_READ")));
        when(rawPermissions.findAllByCodeIn(any())).thenAnswer(call -> ((java.util.Collection<String>) call.getArgument(0)).stream()
                .map(c -> permission(c, 1)).toList());
        permissionsOf("u1");
        permissionsOf("u2");

        UamUser promoted = user("u1", "MANAGER", "ADMIN");
        when(rawUsers.findById("u1")).thenReturn(Optional.of(promoted));
        when(rawUsers.save(promoted)).thenReturn(promoted);
        users.save(promoted);

        assertThat(permissionsOf("u1")).containsExactly("A_READ", "Z_READ");
        permissionsOf("u2");
        verify(rawUsers, times(1)).findById("u2");
    }

    @Test
    void aUserDeleteEvictsThatUser() {
        UamUser u1 = user("u1", "MANAGER");
        when(rawUsers.findById("u1")).thenReturn(Optional.of(u1));
        givenRole("MANAGER", "A_READ");
        permissionsOf("u1");
        when(rawUsers.findById("u1")).thenReturn(Optional.empty());

        users.delete(u1);

        assertThatThrownBy(() -> permissionsOf("u1")).isInstanceOf(UamException.class);
    }

    @Test
    void privilegesAreKeyedByOrganizationAndByUser() {
        when(rawUsers.findById("u1")).thenReturn(Optional.of(user("u1", "MANAGER")));
        when(rawUsers.findById("u2")).thenReturn(Optional.of(user("u2", "SUPPORT")));
        when(rawRoles.findByCodeIgnoreCase("MANAGER")).thenReturn(Optional.of(role("MANAGER", "A_READ")));
        when(rawRoles.findByCodeIgnoreCase("SUPPORT")).thenReturn(Optional.of(role("SUPPORT", "S_READ")));
        when(rawPermissions.findAllByCodeIn(any())).thenAnswer(call -> ((java.util.Collection<String>) call.getArgument(0)).stream()
                .map(c -> permission(c, 1)).toList());

        TenantContext.set("orgA");
        assertThat(permissionsOf("u1")).containsExactly("A_READ");
        assertThat(permissionsOf("u2")).as("another user in the same organization").containsExactly("S_READ");
        TenantContext.set("orgB");
        assertThat(permissionsOf("u1")).containsExactly("A_READ");
        TenantContext.clear();
        assertThat(permissionsOf("u1")).containsExactly("A_READ");

        // u1 loaded once per organization (orgA, orgB, no organization): the entry of org A was not served to org B.
        verify(rawUsers, times(3)).findById("u1");
        verify(rawUsers, times(1)).findById("u2");
    }

    @Test
    void aMissingUserIsNotCached() {
        when(rawUsers.findById("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> permissionsOf("ghost")).isInstanceOf(UamException.class);
        assertThatThrownBy(() -> permissionsOf("ghost")).isInstanceOf(UamException.class);

        verify(rawUsers, times(2)).findById("ghost");
    }

    // ---- my organizations ----

    private void givenMembership(String userId, String tenantId) {
        TenantMembership membership = TenantMembership.builder().tenantId(tenantId).userId(userId)
                .status(TenantMembershipStatus.ACTIVE).build();
        when(rawMemberships.findByUserId(userId)).thenReturn(List.of(membership));
        when(rawTenants.findById(tenantId)).thenReturn(Optional.of(Tenant.builder().id(tenantId).code(tenantId.toUpperCase()).name(tenantId).build()));
        when(rawUsers.findById(userId)).thenReturn(Optional.of(user(userId, "STAFF")));
    }

    @Test
    void organizationsAreCachedAndIsolatedPerOrganizationAndUser() {
        givenMembership("u1", "orgA");
        givenMembership("u2", "orgB");

        TenantContext.set("orgA");
        assertThat(organizations.organizationsOf("u1")).extracting(OrganizationView::tenantId).containsExactly("orgA");
        organizations.organizationsOf("u1");
        assertThat(organizations.organizationsOf("u2")).extracting(OrganizationView::tenantId).containsExactly("orgB");
        TenantContext.set("orgB");
        organizations.organizationsOf("u1");

        verify(rawMemberships, times(2)).findByUserId("u1");
        verify(rawMemberships, times(1)).findByUserId("u2");
    }

    @Test
    void aMembershipChangeEvictsThatUsersOrganizations() {
        givenMembership("u1", "orgA");
        organizations.organizationsOf("u1");
        TenantMembership added = TenantMembership.builder().tenantId("orgB").userId("u1").status(TenantMembershipStatus.ACTIVE).build();
        when(rawMemberships.save(added)).thenReturn(added);

        memberships.save(added);
        organizations.organizationsOf("u1");

        verify(rawMemberships, times(2)).findByUserId("u1");
    }

    @Test
    void aUserOrOrganizationChangeEvictsOrganizations() {
        givenMembership("u1", "orgA");
        organizations.organizationsOf("u1");
        UamUser saved = user("u1", "STAFF");
        when(rawUsers.save(saved)).thenReturn(saved);
        users.save(saved);
        organizations.organizationsOf("u1");
        Tenant renamed = Tenant.builder().id("orgA").code("ORGA").name("Renamed").build();
        when(rawTenants.save(renamed)).thenReturn(renamed);
        tenants.save(renamed);
        organizations.organizationsOf("u1");

        verify(rawMemberships, times(3)).findByUserId("u1");
    }

    // ---- tenants ----

    @Test
    void tenantLookupsAreCachedByIdAndByCodeAndReturnCopies() {
        Tenant stored = Tenant.builder().id("t1").code("ACME").name("Acme").status(TenantStatus.ACTIVE).settings(Map.of("k", "v")).build();
        when(rawTenants.findById("t1")).thenReturn(Optional.of(stored));
        when(rawTenants.findByCodeIgnoreCase("acme")).thenReturn(Optional.of(stored));

        Tenant first = tenants.findById("t1").orElseThrow();
        first.setName("Mutated by caller");
        Tenant second = tenants.findById("t1").orElseThrow();
        tenants.findByCodeIgnoreCase("acme");
        tenants.findByCodeIgnoreCase("ACME");

        assertThat(second.getName()).isEqualTo("Acme");
        assertThat(second).isNotSameAs(first);
        verify(rawTenants, times(1)).findById("t1");
        verify(rawTenants, times(1)).findByCodeIgnoreCase("acme");
    }

    @Test
    void anUnknownTenantIsNotCachedSoANewOrganizationIsSeenAtOnce() {
        when(rawTenants.findById("t9")).thenReturn(Optional.empty());
        assertThat(tenants.findById("t9")).isEmpty();
        Tenant created = Tenant.builder().id("t9").code("NEW").build();
        when(rawTenants.findById("t9")).thenReturn(Optional.of(created));

        assertThat(tenants.findById("t9")).isPresent();
    }

    @Test
    void savingOrDeletingATenantEvictsItAtOnce() {
        Tenant before = Tenant.builder().id("t1").code("ACME").name("Old").status(TenantStatus.ACTIVE).build();
        when(rawTenants.findById("t1")).thenReturn(Optional.of(before));
        tenants.findById("t1");
        Tenant suspended = Tenant.builder().id("t1").code("ACME").name("Old").status(TenantStatus.SUSPENDED).build();
        when(rawTenants.findById("t1")).thenReturn(Optional.of(suspended));
        when(rawTenants.save(suspended)).thenReturn(suspended);

        tenants.save(suspended);

        assertThat(tenants.findById("t1").orElseThrow().getStatus()).isEqualTo(TenantStatus.SUSPENDED);
        tenants.delete(suspended);
        tenants.findById("t1");
        verify(rawTenants, times(3)).findById("t1");
    }

    // ---- application codes ----

    private static TenantApplication subscription(String tenantId, String code) {
        return TenantApplication.builder().tenantId(tenantId).applicationCode(code).status(TenantApplicationStatus.ACTIVE).build();
    }

    @Test
    void applicationCodesAreCachedPerOrganizationAndEvictedBySubscriptionAndTenantChanges() {
        when(rawApplications.findByTenantId("orgA")).thenReturn(List.of(subscription("orgA", "RIDER")));
        when(rawApplications.findByTenantId("orgB")).thenReturn(List.of(subscription("orgB", "MATRIMONY")));

        assertThat(applicationCodes.activeApplicationCodes("orgA")).containsExactly("RIDER");
        assertThat(applicationCodes.activeApplicationCodes("orgB")).containsExactly("MATRIMONY");
        applicationCodes.activeApplicationCodes("orgA");
        verify(rawApplications, times(1)).findByTenantId("orgA");

        TenantApplication added = subscription("orgA", "MATRIMONY");
        when(rawApplications.save(added)).thenReturn(added);
        when(rawApplications.findByTenantId("orgA")).thenReturn(List.of(subscription("orgA", "RIDER"), added));
        applications.save(added);

        assertThat(applicationCodes.activeApplicationCodes("orgA")).containsExactly("MATRIMONY", "RIDER");
        applicationCodes.activeApplicationCodes("orgB");
        verify(rawApplications, times(1)).findByTenantId("orgB");

        tenants.delete(Tenant.builder().id("orgB").build());
        applicationCodes.activeApplicationCodes("orgB");
        verify(rawApplications, times(2)).findByTenantId("orgB");
    }

    // ---- switch ----

    @Test
    void whenDisabledEveryPathReadsTheDatabaseEachTime() {
        UamCacheProperties off = new UamCacheProperties();
        off.setEnabled(false);
        wire(off);
        when(rawUsers.findById("u1")).thenReturn(Optional.of(user("u1", "MANAGER")));
        givenRole("MANAGER", "A_READ");
        when(rawTenants.findById("t1")).thenReturn(Optional.of(Tenant.builder().id("t1").build()));
        when(rawApplications.findByTenantId("t1")).thenReturn(List.of());

        permissionsOf("u1");
        permissionsOf("u1");
        tenants.findById("t1");
        tenants.findById("t1");
        applicationCodes.activeApplicationCodes("t1");
        applicationCodes.activeApplicationCodes("t1");

        verify(rawUsers, times(2)).findById("u1");
        verify(rawTenants, times(2)).findById("t1");
        verify(rawApplications, times(2)).findByTenantId("t1");
        verify(rawRoles, never()).save(any());
    }

    @Test
    void aSingleCacheCanBeSwitchedOffOnItsOwn() {
        UamCacheProperties partial = new UamCacheProperties();
        partial.getTenants().setEnabled(false);
        wire(partial);
        when(rawTenants.findById("t1")).thenReturn(Optional.of(Tenant.builder().id("t1").build()));
        when(rawUsers.findById("u1")).thenReturn(Optional.of(user("u1", "MANAGER")));
        givenRole("MANAGER", "A_READ");

        tenants.findById("t1");
        tenants.findById("t1");
        permissionsOf("u1");
        permissionsOf("u1");

        verify(rawTenants, times(2)).findById("t1");
        verify(rawUsers, times(1)).findById("u1");
    }

    @Test
    void writesAndReadsOfOtherMethodsPassThroughTheProxyUnchanged() {
        when(rawUsers.existsByUserNameIgnoreCase("bob")).thenReturn(true);
        assertThat(users.existsByUserNameIgnoreCase("bob")).isTrue();
        assertThat(properties.isEnabled()).isTrue();
    }
}
