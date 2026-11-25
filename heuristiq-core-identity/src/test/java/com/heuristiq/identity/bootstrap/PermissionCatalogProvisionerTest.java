package com.heuristiq.identity.bootstrap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heuristiq.identity.application.Application;
import com.heuristiq.identity.application.repository.ApplicationRepository;
import com.heuristiq.identity.permissions.Permission;
import com.heuristiq.identity.permissions.PermissionRepository;
import com.heuristiq.identity.role.UamRole;
import com.heuristiq.identity.role.repository.UamRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.data.domain.Sort;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PermissionCatalogProvisionerTest {
    private final PermissionCatalogLoader loader = new PermissionCatalogLoader(new ObjectMapper());
    private final PermissionRepository permissions = mock(PermissionRepository.class);
    private final ApplicationRepository applications = mock(ApplicationRepository.class);
    private final UamRoleRepository roles = mock(UamRoleRepository.class);
    private final Map<String, Permission> stored = new LinkedHashMap<>();
    private final Map<String, Application> products = new LinkedHashMap<>();
    private final Map<String, UamRole> roleRows = new HashMap<>();
    private PermissionCatalogProvisioner provisioner;

    @BeforeEach
    void prepare() {
        when(permissions.findByCodeIgnoreCase(anyString())).thenAnswer(call -> Optional.ofNullable(stored.get(call.getArgument(0))));
        when(permissions.save(any())).thenAnswer(call -> {
            Permission permission = call.getArgument(0); stored.put(permission.getCode(), permission); return permission;
        });
        when(applications.findAll(any(Sort.class))).thenAnswer(call -> new ArrayList<>(products.values()));
        when(applications.save(any())).thenAnswer(call -> {
            Application app = call.getArgument(0); products.put(app.getCode(), app); return app;
        });
        when(roles.findByCodeIgnoreCase(anyString())).thenAnswer(call -> Optional.ofNullable(roleRows.get(call.getArgument(0))));
        when(roles.save(any())).thenAnswer(call -> {
            UamRole role = call.getArgument(0); roleRows.put(role.getCode(), role); return role;
        });
        provisioner = new PermissionCatalogProvisioner(loader, permissions, applications, roles);
    }

    private List<String> expectedCodes(String applicationCode) {
        return java.util.stream.Stream.concat(loader.loadShared().stream(), loader.loadApplication(applicationCode).stream())
                .map(PermissionCatalogEntry::code).toList();
    }

    private void startShared() {
        List<PermissionCatalogEntry> entries = provisioner.bootstrap();
        roleRows.put("SUPER_ADMIN", UamRole.builder().code("SUPER_ADMIN")
                .permissions(entries.stream().map(PermissionCatalogEntry::code).toList()).build());
        for (String code : List.of("DRIVER", "CUSTOMER", "AGENT")) {
            roleRows.put(code, UamRole.builder().code(code).permissions(new ArrayList<>(List.of("CUSTOM_GRANT"))).build());
        }
    }

    @Test
    void freshStartupStoresOnlySharedPrivileges() {
        assertThat(provisioner.bootstrap()).hasSize(loader.loadShared().size());
        assertThat(stored).hasSize(loader.loadShared().size());
        assertThat(stored.values()).allSatisfy(permission -> {
            assertThat(permission.getAttributes().get("privilegeCode").toString()).startsWith("SHARED_");
            assertThat(permission.getAttributes()).containsEntry("privilegeCatalogVersion", 2);
            assertThat(permission.getApplicationCode()).isEqualTo("UAM");
        });
        assertThat(products).isEmpty();
    }

    @Test
    void springWiresSharedStartupAndApplicationCreationToTheSameProvisioner() throws Exception {
        var subscription = mock(com.heuristiq.identity.organization.subscription.TenantApplicationServiceUseCase.class);
        var session = mock(com.heuristiq.identity.application.SessionOrganizationResolver.class);
        when(session.sessionOrganizationId()).thenReturn("org-a");
        try (var context = new org.springframework.context.annotation.AnnotationConfigApplicationContext()) {
            context.registerBean(PermissionCatalogLoader.class, () -> loader);
            context.registerBean(PermissionRepository.class, () -> permissions);
            context.registerBean(ApplicationRepository.class, () -> applications);
            context.registerBean(UamRoleRepository.class, () -> roles);
            context.registerBean(com.heuristiq.identity.organization.subscription.TenantApplicationServiceUseCase.class, () -> subscription);
            context.registerBean(com.heuristiq.identity.application.SessionOrganizationResolver.class, () -> session);
            context.registerBean(com.heuristiq.identity.application.access.repository.ApplicationAccessRepository.class,
                    () -> mock(com.heuristiq.identity.application.access.repository.ApplicationAccessRepository.class));
            context.registerBean(PermissionCatalogProvisioner.class);
            context.registerBean(com.heuristiq.identity.application.ApplicationService.class);
            context.refresh();
            var runner = new UamBootstrapRunner(roles, context.getBean(PermissionCatalogProvisioner.class),
                    mock(com.heuristiq.identity.users.repository.UamUserRepository.class),
                    mock(com.heuristiq.identity.usercredentials.UserCredentialServiceUseCase.class), "", "", "", "",
                    mock(com.heuristiq.identity.organization.repository.TenantRepository.class),
                    com.heuristiq.identity.organization.PlatformOrganizationProperties.defaults(),
                    mock(com.heuristiq.identity.organization.membership.TenantMembershipServiceUseCase.class));
            runner.run();
            assertThat(stored).hasSize(loader.loadShared().size());
            for (UamRole role : roleRows.values()) assertThat(stored.keySet()).containsAll(role.getPermissions());
            var request = new ObjectMapper().readValue("{\"code\":\"BESTRIDE_RIDER_APP\",\"authenticationMode\":\"LOCAL\"}",
                    com.heuristiq.identity.application.UpsertApplicationRequest.class);
            context.getBean(com.heuristiq.identity.application.ApplicationService.class).create(
                    new com.heuristiq.identity.application.ApplicationServiceUseCase.CreateInput(request));
            assertThat(stored).hasSize(expectedCodes("BESTRIDE_RIDER_APP").size());
            assertThat(roleRows.get("SUPER_ADMIN").getPermissions()).hasSize(expectedCodes("BESTRIDE_RIDER_APP").size());
            assertThat(products.get("BESTRIDE_RIDER_APP").getAttributes())
                    .containsEntry(PermissionCatalogProvisioner.IMPORT_STATUS, "COMPLETE");
        }
    }

    @ParameterizedTest
    @CsvSource({"BESTRIDE_RIDER_APP,RIDE", "TRAIN_BOOKING_APP,TRAIN", "PASUPU_KUMKUMA_MATRIMONY_APP,MATRIMONY",
            "FOOD_DELIVER_APP,FOOD", "FRANCHISE_MANAGEMENT_APP,FRANCHISE", "CENTRALISED_WALLET_APP,WALLET", "BEST_PURCHASE_APP,IMS"})
    void creatingOneApplicationImportsOnlyItsOwnGroup(String code, String prefix) {
        startShared();
        Application app = Application.builder().id(code).code(code).build();
        products.put(code, app);
        provisioner.provisionApplication(app);
        assertThat(stored.keySet()).containsExactlyInAnyOrderElementsOf(expectedCodes(code));
        assertThat(stored.values()).allSatisfy(permission -> {
            String readable = permission.getAttributes().get("privilegeCode").toString();
            assertThat(readable.startsWith("SHARED_") || readable.startsWith(prefix + "_")).isTrue();
            if (readable.startsWith(prefix + "_")) assertThat(permission.getApplicationCode()).isEqualTo(code);
        });
        assertThat(app.getAttributes()).containsEntry(PermissionCatalogProvisioner.IMPORT_STATUS, "COMPLETE");
        assertThat(roleRows.get("SUPER_ADMIN").getPermissions()).containsExactlyInAnyOrderElementsOf(expectedCodes(code));
        for (String role : List.of("DRIVER", "CUSTOMER", "AGENT")) assertThat(roleRows.get(role).getPermissions()).contains("CUSTOM_GRANT");
        provisioner.provisionApplication(app);
        assertThat(stored.keySet()).containsExactlyInAnyOrderElementsOf(expectedCodes(code));
    }

    @Test
    void repeatImportsAndRestartsPreserveRoleCustomizationsAndRepairMissingRows() {
        startShared();
        Application app = Application.builder().code("BESTRIDE_RIDER_APP").build();
        products.put(app.getCode(), app);
        provisioner.provisionApplication(app);
        assertThat(roleRows.get("DRIVER").getPermissions()).contains("RIDE_CASH_CONFIRM");
        roleRows.get("DRIVER").getPermissions().remove("RIDE_CASH_CONFIRM");
        stored.remove("RIDE_READ");
        assertThat(provisioner.bootstrap()).hasSize(expectedCodes("BESTRIDE_RIDER_APP").size());
        provisioner.provisionApplication(app);
        assertThat(stored).hasSize(expectedCodes("BESTRIDE_RIDER_APP").size()).containsKey("RIDE_READ");
        assertThat(roleRows.get("DRIVER").getPermissions()).contains("CUSTOM_GRANT").doesNotContain("RIDE_CASH_CONFIRM");
    }

    @Test
    void interruptedImportsStayPendingAndCanBeRetriedWithoutDuplicatePrivileges() {
        startShared();
        Application app = Application.builder().code("BESTRIDE_RIDER_APP").build();
        products.put(app.getCode(), app);
        AtomicBoolean fail = new AtomicBoolean(true);
        doAnswer(call -> {
            Permission permission = call.getArgument(0);
            if (permission.getAttributes().get("privilegeCode").toString().startsWith("RIDE_") && fail.getAndSet(false)) {
                throw new IllegalStateException("temporary persistence failure");
            }
            stored.put(permission.getCode(), permission); return permission;
        }).when(permissions).save(any());
        assertThatThrownBy(() -> provisioner.provisionApplication(app)).hasMessageContaining("temporary persistence failure");
        assertThat(app.getAttributes()).containsEntry(PermissionCatalogProvisioner.IMPORT_STATUS, "PENDING");
        assertThat(roleRows.get("SUPER_ADMIN").getPermissions()).hasSize(loader.loadShared().size());
        assertThat(roleRows.get("DRIVER").getPermissions()).doesNotContain("RIDE_CASH_CONFIRM");
        provisioner.provisionApplication(app);
        assertThat(app.getAttributes()).containsEntry(PermissionCatalogProvisioner.IMPORT_STATUS, "COMPLETE");
        assertThat(stored).hasSize(expectedCodes("BESTRIDE_RIDER_APP").size());
    }
}
