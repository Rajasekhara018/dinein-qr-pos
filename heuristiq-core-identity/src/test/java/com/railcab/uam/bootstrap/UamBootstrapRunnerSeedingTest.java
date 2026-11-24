package com.heuristiq.uam.bootstrap;

import com.heuristiq.uam.common.UamCommonConstants;
import com.heuristiq.uam.enums.UamEnums;
import com.heuristiq.uam.helper.AuditInfo;
import com.heuristiq.uam.helper.RPhoneNumber;
import com.heuristiq.uam.organization.PlatformOrganizationProperties;
import com.heuristiq.uam.organization.Tenant;
import com.heuristiq.uam.organization.membership.TenantMembership;
import com.heuristiq.uam.organization.membership.TenantMembershipService;
import com.heuristiq.uam.organization.membership.repository.TenantMembershipRepository;
import com.heuristiq.uam.organization.membership.TenantMembershipServiceUseCase;
import com.heuristiq.uam.organization.repository.TenantRepository;
import com.heuristiq.uam.permissions.Permission;
import com.heuristiq.uam.permissions.PermissionRepository;
import com.heuristiq.uam.role.UamRole;
import com.heuristiq.uam.role.repository.UamRoleRepository;
import com.heuristiq.uam.usercredentials.UserCredentialServiceUseCase;
import com.heuristiq.uam.users.UamUser;
import com.heuristiq.uam.users.repository.UamUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

/** What {@link UamBootstrapRunner} seeds at start-up: the permission catalogue, the structural roles and the bootstrap admin. */
class UamBootstrapRunnerSeedingTest {
    private UamRoleRepository roles;
    private PermissionRepository permissions;
    private UamUserRepository users;
    private UserCredentialServiceUseCase credentials;
    private PermissionCatalogLoader loader;
    private TenantRepository tenants;
    private TenantMembershipServiceUseCase memberships;
    private final PermissionCatalogEntry entry = new PermissionCatalogEntry(7, "USERS_LIST", "/api/v1/uam/users",
            UamEnums.ModuleCategory.values()[0], UamEnums.ResourceType.MASTER, UamEnums.PermissionAction.CREATE, "Lists users");

    @BeforeEach
    void setUp() {
        roles = mock(UamRoleRepository.class);
        permissions = mock(PermissionRepository.class);
        users = mock(UamUserRepository.class);
        credentials = mock(UserCredentialServiceUseCase.class);
        loader = mock(PermissionCatalogLoader.class);
        tenants = mock(TenantRepository.class);
        memberships = mock(TenantMembershipServiceUseCase.class);
        when(loader.loadShared()).thenReturn(List.of(entry));
        when(permissions.findByCodeIgnoreCase(any())).thenReturn(Optional.empty());
        when(roles.findByCodeIgnoreCase(any())).thenReturn(Optional.empty());
        when(users.save(any(UamUser.class))).thenAnswer(inv -> {
            UamUser u = inv.getArgument(0);
            u.setId("admin-id");
            return u;
        });
    }

    private UamBootstrapRunner runner(String username, String password, String email, String phone) {
        return new UamBootstrapRunner(roles, provisioner(), users, credentials, username, password, email, phone,
                tenants, PlatformOrganizationProperties.defaults(), memberships);
    }

    private PermissionCatalogProvisioner provisioner() {
        return new PermissionCatalogProvisioner(loader, permissions,
                mock(com.heuristiq.uam.application.repository.ApplicationRepository.class), roles);
    }

    @Test
    void newPermissionsAreCreatedFromTheCatalogueWithGatewayAttributes() {
        runner("", "", "", "").run();

        ArgumentCaptor<Permission> captor = ArgumentCaptor.forClass(Permission.class);
        verify(permissions).save(captor.capture());
        Permission saved = captor.getValue();
        assertThat(saved.getCode()).isEqualTo("USERS_LIST");
        assertThat(saved.getApplicationCode()).isEqualTo("UAM");
        assertThat(saved.getSystemPermission()).isTrue();
        assertThat(saved.getDisplayName()).isEqualTo("USERS LIST");
        assertThat(saved.getDescription()).isEqualTo("Lists users");
        assertThat(saved.getAttributes()).containsEntry(UamCommonConstants.PERMISSION_PRIVILEGE_ID_ATTRIBUTE, 7)
                .containsEntry(UamCommonConstants.PERMISSION_CATALOG_VERSION_ATTRIBUTE, 2)
                .containsEntry(UamCommonConstants.PERMISSION_API_PATH_ATTRIBUTE, "/api/v1/uam/users");
    }

    @Test
    void existingPermissionsAreBackfilledKeepingTheirOtherAttributes() {
        Permission existing = Permission.builder().code("USERS_LIST").audit(AuditInfo.builder().build())
                .attributes(new HashMap<>(Map.of("custom", "keep", "privilegeId", 99, "privilegeCatalogVersion", 1))).build();
        when(permissions.findByCodeIgnoreCase("USERS_LIST")).thenReturn(Optional.of(existing));

        runner("", "", "", "").run();

        verify(permissions).save(existing);
        assertThat(existing.getAttributes()).containsEntry("custom", "keep")
                .containsEntry(UamCommonConstants.PERMISSION_PRIVILEGE_ID_ATTRIBUTE, 7)
                .containsEntry(UamCommonConstants.PERMISSION_CATALOG_VERSION_ATTRIBUTE, 2);
        assertThat(existing.getAudit().getUpdatedAt()).isNotNull();
    }

    @Test
    void existingPermissionWithoutAuditStillSaves() {
        Permission existing = Permission.builder().code("USERS_LIST").build();
        when(permissions.findByCodeIgnoreCase("USERS_LIST")).thenReturn(Optional.of(existing));
        runner("", "", "", "").run();
        verify(permissions).save(existing);
    }

    @Test
    void superAdminRoleIsCreatedWithEveryCataloguePermissionAndStructuralRolesWithTheirDefaults() {
        runner("", "", "", "").run();

        ArgumentCaptor<UamRole> captor = ArgumentCaptor.forClass(UamRole.class);
        verify(roles, org.mockito.Mockito.atLeast(1 + BootstrapRoleConstants.STRUCTURAL_ROLE_PERMISSIONS.size())).save(captor.capture());
        UamRole superAdmin = captor.getAllValues().stream().filter(r -> "SUPER_ADMIN".equals(r.getCode())).findFirst().orElseThrow();
        assertThat(superAdmin.getPermissions()).containsExactly("USERS_LIST");
        assertThat(superAdmin.getEditable()).isFalse();
        assertThat(superAdmin.getSystemRole()).isTrue();
        for (var structural : BootstrapRoleConstants.STRUCTURAL_ROLE_PERMISSIONS.entrySet()) {
            UamRole role = captor.getAllValues().stream().filter(r -> structural.getKey().equals(r.getCode())).findFirst().orElseThrow();
            assertThat(role.getPermissions()).isEmpty(); // No configured business application; the fixture has only USERS_LIST.
            assertThat(role.getAttributes()).containsEntry(BootstrapRoleConstants.PERMISSIONS_VERSION_ATTRIBUTE,
                    BootstrapRoleConstants.STRUCTURAL_ROLE_PERMISSIONS_VERSION);
        }
    }

    @Test
    void existingSuperAdminIsResyncedToEveryPermissionEachBoot() {
        UamRole existing = UamRole.builder().code("SUPER_ADMIN").permissions(List.of("OLD")).audit(AuditInfo.builder().build()).build();
        when(roles.findByCodeIgnoreCase("SUPER_ADMIN")).thenReturn(Optional.of(existing));

        runner("", "", "", "").run();

        assertThat(existing.getPermissions()).containsExactly("USERS_LIST");
        assertThat(existing.getAudit().getUpdatedAt()).isNotNull();
        verify(roles).save(existing);
    }

    @Test
    void outdatedStructuralRoleGetsDefaultsMergedWithoutLosingAdminAdditions() {
        String code = "CUSTOMER";
        List<String> defaults = List.of("NMS_IN_APP_ACCESS");
        when(loader.loadShared()).thenReturn(List.of(new PermissionCatalogEntry(7, "NMS_IN_APP_ACCESS", entry.apipath(),
                entry.module(), entry.resource(), entry.action(), entry.description())));
        UamRole existing = UamRole.builder().code(code).permissions(new ArrayList<>(List.of("ADMIN_ADDED")))
                .attributes(new HashMap<>(Map.of(BootstrapRoleConstants.PERMISSIONS_VERSION_ATTRIBUTE, 1)))
                .audit(AuditInfo.builder().build()).build();
        when(roles.findByCodeIgnoreCase(code)).thenReturn(Optional.of(existing));

        runner("", "", "", "").run();

        assertThat(existing.getPermissions()).contains("ADMIN_ADDED").containsAll(defaults);
        assertThat(existing.getAttributes()).containsEntry(BootstrapRoleConstants.PERMISSIONS_VERSION_ATTRIBUTE,
                BootstrapRoleConstants.STRUCTURAL_ROLE_PERMISSIONS_VERSION);
        verify(roles).save(existing);
    }

    @Test
    void customerRoleLosesTheCashAndUpiConfirmationItWasWronglySeededWith() {
        UamRole existing = UamRole.builder().code("CUSTOMER")
                .permissions(new ArrayList<>(List.of("BOOKINGS_CONFIRM_CASH_PAYMENT", "BOOKINGS_CONFIRM_UPI_PAYMENT", "ADMIN_ADDED")))
                .attributes(new HashMap<>(Map.of(BootstrapRoleConstants.PERMISSIONS_VERSION_ATTRIBUTE, 8)))
                .audit(AuditInfo.builder().build()).build();
        when(roles.findByCodeIgnoreCase("CUSTOMER")).thenReturn(Optional.of(existing));

        runner("", "", "", "").run();

        assertThat(existing.getPermissions()).contains("ADMIN_ADDED")
                .doesNotContain("BOOKINGS_CONFIRM_CASH_PAYMENT", "BOOKINGS_CONFIRM_UPI_PAYMENT");
        assertThat(BootstrapRoleConstants.CUSTOMER_PERMISSIONS)
                .doesNotContain("BOOKINGS_CONFIRM_CASH_PAYMENT", "BOOKINGS_CONFIRM_UPI_PAYMENT");
    }

    @Test
    void driverKeepsConfirmingCashPayments() {
        assertThat(BootstrapRoleConstants.DRIVER_PERMISSIONS).contains("RIDE_CASH_CONFIRM")
                .doesNotContain("BOOKINGS_CONFIRM_CASH_PAYMENT");
    }

    @Test
    void outdatedRolesLoseLegacyBookingsGrantsAndKeepMicroserviceAndCustomGrants() {
        for (String code : List.of("DRIVER", "CUSTOMER")) {
            UamRole existing = UamRole.builder().code(code)
                    .permissions(new ArrayList<>(List.of("BOOKINGS_READ", "BOOKINGS_REQUEST_RIDE", "RIDE_READ", "ADMIN_ADDED")))
                    .attributes(new HashMap<>(Map.of(BootstrapRoleConstants.PERMISSIONS_VERSION_ATTRIBUTE, 11)))
                    .audit(AuditInfo.builder().build()).build();
            when(roles.findByCodeIgnoreCase(code)).thenReturn(Optional.of(existing));
            runner("", "", "", "").run();
            assertThat(existing.getPermissions()).contains("RIDE_READ", "ADMIN_ADDED")
                    .noneMatch(permission -> permission.startsWith("BOOKINGS_"));
            assertThat(existing.getAttributes()).containsEntry(BootstrapRoleConstants.PERMISSIONS_VERSION_ATTRIBUTE,
                    BootstrapRoleConstants.STRUCTURAL_ROLE_PERMISSIONS_VERSION);
        }
    }

    @Test
    void structuralRoleWithoutVersionOrPermissionsIsTreatedAsVersionOne() {
        String code = "CUSTOMER";
        when(loader.loadShared()).thenReturn(List.of(new PermissionCatalogEntry(7, "NMS_IN_APP_ACCESS", entry.apipath(),
                entry.module(), entry.resource(), entry.action(), entry.description())));
        UamRole existing = UamRole.builder().code(code).permissions(null).attributes(null).build();
        when(roles.findByCodeIgnoreCase(code)).thenReturn(Optional.of(existing));

        runner("", "", "", "").run();

        assertThat(existing.getPermissions()).containsExactly("NMS_IN_APP_ACCESS");
        assertThat(existing.getAttributes()).containsKey(BootstrapRoleConstants.PERMISSIONS_VERSION_ATTRIBUTE);
    }

    @Test
    void structuralRoleAtTheCurrentVersionIsLeftAlone() {
        String code = BootstrapRoleConstants.STRUCTURAL_ROLE_PERMISSIONS.keySet().iterator().next();
        UamRole existing = UamRole.builder().code(code).permissions(new ArrayList<>(List.of("ONLY_THIS")))
                .attributes(new HashMap<>(Map.of(BootstrapRoleConstants.PERMISSIONS_VERSION_ATTRIBUTE,
                        BootstrapRoleConstants.STRUCTURAL_ROLE_PERMISSIONS_VERSION))).build();
        when(roles.findByCodeIgnoreCase(code)).thenReturn(Optional.of(existing));

        runner("", "", "", "").run();

        assertThat(existing.getPermissions()).containsExactly("ONLY_THIS");
        verify(roles, never()).save(existing);
    }

    @Test
    void noBootstrapAdminIsCreatedWithoutCredentials() {
        runner("", "pw", "", "").run();
        runner("admin", " ", "", "").run();
        verify(users, never()).save(any());
        verify(credentials, never()).setPassword(any());
    }

    @Test
    void bootstrapAdminIsCreatedInThePlatformOrganizationWithAPassword() {
        when(users.findByUserNameIgnoreCase("admin")).thenReturn(Optional.empty());
        when(users.findByEmailIgnoreCase("admin@x.com")).thenReturn(Optional.empty());
        when(tenants.findByCodeIgnoreCase("DEFAULT")).thenReturn(Optional.of(Tenant.builder().id("default").code("DEFAULT").build()));

        runner(" admin ", "Secret#1", " admin@x.com ", "+919876543210").run();

        ArgumentCaptor<UamUser> captor = ArgumentCaptor.forClass(UamUser.class);
        verify(users).save(captor.capture());
        UamUser admin = captor.getValue();
        assertThat(admin.getUserName()).isEqualTo("admin");
        assertThat(admin.getEmail()).isEqualTo("admin@x.com");
        assertThat(admin.getRoles()).containsExactly("SUPER_ADMIN");
        assertThat(admin.getTenantId()).isEqualTo("default");
        assertThat(admin.getPhoneNumber().hasValue()).isTrue();
        verify(credentials).setPassword(new UserCredentialServiceUseCase.SetPasswordInput("admin-id", "Secret#1", false));
    }

    @Test
    void aFreshlySeededAdminGetsItsMembershipInTheSameStart() {
        when(users.findByUserNameIgnoreCase("admin")).thenReturn(Optional.empty());
        when(tenants.findByCodeIgnoreCase("DEFAULT")).thenReturn(Optional.of(Tenant.builder().id("default").code("DEFAULT").build()));

        runner("admin", "Secret#1", "", "").run();

        ArgumentCaptor<UamUser> captor = ArgumentCaptor.forClass(UamUser.class);
        verify(memberships).ensureAnyMembership(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo("admin-id");
        assertThat(captor.getValue().getTenantId()).isEqualTo("default");
    }

    @Test
    void theAdminMembershipIsInTheGeneratedIdDefaultOrganizationAndIsCreatedOnce() {
        // Mongo shape: the DEFAULT organization has a generated id, not the literal "default".
        Tenant defaultOrg = Tenant.builder().id("65f0c0ffee0000000000abcd").code("DEFAULT").status(UamEnums.TenantStatus.ACTIVE).build();
        when(tenants.findByCodeIgnoreCase("DEFAULT")).thenReturn(Optional.of(defaultOrg));
        when(tenants.findById(defaultOrg.getId())).thenReturn(Optional.of(defaultOrg));
        when(users.findByUserNameIgnoreCase("admin")).thenReturn(Optional.empty());
        TenantMembershipRepository membershipRows = mock(TenantMembershipRepository.class);
        List<TenantMembership> saved = new ArrayList<>();
        when(membershipRows.findByUserId("admin-id")).thenAnswer(inv -> List.copyOf(saved));
        when(membershipRows.save(any(TenantMembership.class))).thenAnswer(inv -> {
            saved.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
        TenantMembershipService real = new TenantMembershipService(membershipRows, tenants, users, PlatformOrganizationProperties.defaults());
        UamBootstrapRunner runner = new UamBootstrapRunner(roles, provisioner(), users, credentials, "admin", "Secret#1", "", "",
                tenants, PlatformOrganizationProperties.defaults(), real);

        runner.run();
        ArgumentCaptor<UamUser> captor = ArgumentCaptor.forClass(UamUser.class);
        verify(users).save(captor.capture());
        UamUser admin = captor.getValue();
        when(users.findByUserNameIgnoreCase("admin")).thenReturn(Optional.of(admin));
        runner.run();
        real.ensureAnyMembership(admin);

        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getTenantId()).isEqualTo("65f0c0ffee0000000000abcd");
        assertThat(saved.get(0).getUserId()).isEqualTo("admin-id");
    }

    @Test
    void aFailingMembershipStepNeverStopsTheStart() {
        when(users.findByUserNameIgnoreCase("admin")).thenReturn(Optional.empty());
        when(memberships.ensureAnyMembership(any())).thenThrow(new IllegalStateException("db down"));

        runner("admin", "Secret#1", "", "").run();

        verify(credentials).setPassword(new UserCredentialServiceUseCase.SetPasswordInput("admin-id", "Secret#1", false));
    }

    @Test
    void bootstrapAdminWithoutEmailPhoneOrOrganizationStillGetsCreated() {
        when(users.findByUserNameIgnoreCase("admin")).thenReturn(Optional.empty());
        when(tenants.findByCodeIgnoreCase("DEFAULT")).thenReturn(Optional.empty());

        runner("admin", "Secret#1", "", "").run();

        ArgumentCaptor<UamUser> captor = ArgumentCaptor.forClass(UamUser.class);
        verify(users).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isNull();
        assertThat(captor.getValue().getTenantId()).isNull();
        assertThat(captor.getValue().getPhoneNumber().hasValue()).isFalse();
    }

    @Test
    void bootstrapAdminIsSkippedWhenTheEmailBelongsToAnotherUsername() {
        when(users.findByUserNameIgnoreCase("admin")).thenReturn(Optional.empty());
        when(users.findByEmailIgnoreCase("admin@x.com")).thenReturn(Optional.of(UamUser.builder().id("other").build()));

        runner("admin", "Secret#1", "admin@x.com", "").run();

        verify(users, never()).save(any());
        verify(credentials, never()).setPassword(any());
    }

    @Test
    void existingBootstrapAdminIsSyncedNotRecreated() {
        UamUser existing = UamUser.builder().id("a1").userName("admin").roles(List.of("SUPER_ADMIN"))
                .phoneNumber(new RPhoneNumber("+919876543210")).build();
        when(users.findByUserNameIgnoreCase("admin")).thenReturn(Optional.of(existing));

        runner("admin", "Secret#1", "admin@x.com", "+919876543210").run();

        assertThat(existing.getFullName().getFullName().trim()).isEqualTo("RealBoom Super Admin");
        verify(users).save(existing);
        verify(credentials, never()).setPassword(any());
    }

    @Test
    void syncOfAnAdminWithoutAnyPhoneIsSkippedAndAFailingSaveNeverStopsStartup() {
        UamUser noPhone = UamUser.builder().id("a1").roles(List.of("SUPER_ADMIN")).phoneNumber(null).build();
        runner("admin", "pw", "", "").syncExistingAdmin(noPhone);
        verify(users, never()).save(noPhone);

        UamUser admin = UamUser.builder().id("a2").roles(null).phoneNumber(new RPhoneNumber("+919876543210")).build();
        doThrow(new IllegalStateException("db down")).when(users).save(admin);
        runner("admin", "pw", "", "").syncExistingAdmin(admin);
        assertThat(admin.getRoles()).contains("SUPER_ADMIN");
    }
}
