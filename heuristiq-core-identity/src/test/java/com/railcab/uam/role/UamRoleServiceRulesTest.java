package com.heuristiq.uam.role;

import com.heuristiq.uam.common.UamException;
import com.heuristiq.uam.dto.UpsertRoleRequest;
import com.heuristiq.uam.helper.AuditInfo;
import com.heuristiq.uam.identity.IdentityProviderSyncService;
import com.heuristiq.uam.role.repository.UamRoleRepository;
import com.heuristiq.uam.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Update, delete, listing and visibility rules of {@link UamRoleService}. */
class UamRoleServiceRulesTest {
    private UamRoleRepository roles;
    private IdentityProviderSyncService idp;
    private UamRoleService service;
    private com.heuristiq.uam.users.repository.UamUserRepository users;

    @BeforeEach
    void setUp() {
        roles = mock(UamRoleRepository.class);
        idp = mock(IdentityProviderSyncService.class);
        when(roles.save(any(UamRole.class))).thenAnswer(inv -> inv.getArgument(0));
        users = mock(com.heuristiq.uam.users.repository.UamUserRepository.class);
        service = new UamRoleService(roles, idp, users);
        // the signed-in organization admin carries these permissions, so they may hand them out
        when(roles.findByCodeIgnoreCase("ORG_ADMIN")).thenReturn(Optional.of(
                UamRole.builder().id("oa").code("ORG_ADMIN").permissions(List.of("A_READ", "B_WRITE", "P")).build()));
        TenantContext.set("acme");
        signIn("ROLE_ORG_ADMIN");
    }

    @AfterEach
    void clear() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private void signIn(String authority) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("u", "x", authority));
    }

    private static UpsertRoleRequest request(String code, String name, Set<String> permissions, boolean system) {
        return new UpsertRoleRequest(code, name, " d ", permissions, system);
    }

    @Test
    void createNormalizesCodesAndNeverSharesForAnOrgAdmin() {
        Set<String> permissions = new LinkedHashSet<>(List.of(" a_read ", "", "A_READ", "b_write"));

        UamRole role = service.create(new UamRoleServiceUseCase.CreateInput(request(" ops ", " ", permissions, false))).role();

        assertThat(role.getCode()).isEqualTo("OPS");
        assertThat(role.getName()).isEqualTo("OPS");
        assertThat(role.getDescription()).isEqualTo("d");
        assertThat(role.getPermissions()).containsExactly("A_READ", "B_WRITE");
        assertThat(role.getTenantId()).isEqualTo("acme");
        assertThat(role.getSystemRole()).isFalse();
        verify(idp).syncRole(role);
    }

    @Test
    void anOrganizationAdminCannotGrantAPermissionTheyDoNotHold() {
        assertThatThrownBy(() -> service.create(new UamRoleServiceUseCase.CreateInput(request("grab", null, Set.of("A_READ", "ORGANIZATION_CREATE"), false))))
                .isInstanceOfSatisfying(UamException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(e.getMessage()).contains("ORGANIZATION_CREATE").doesNotContain("A_READ");
                });
        verify(roles, never()).save(any());
    }

    @Test
    void anUpdateMayKeepWhatTheAdminDoesNotHoldButCannotAddToIt() {
        UamRole own = UamRole.builder().id("r1").code("OPS").tenantId("acme").permissions(List.of("ORGANIZATION_CREATE")).audit(AuditInfo.builder().build()).build();
        when(roles.findById("r1")).thenReturn(Optional.of(own));
        when(roles.findByCodeIgnoreCase("OPS")).thenReturn(Optional.of(own));

        UamRole kept = service.update(new UamRoleServiceUseCase.UpdateInput("r1", request("ops", "Ops", Set.of("ORGANIZATION_CREATE", "P"), false))).role();
        assertThat(kept.getPermissions()).containsExactly("ORGANIZATION_CREATE", "P");

        assertThatThrownBy(() -> service.update(new UamRoleServiceUseCase.UpdateInput("r1", request("ops", "Ops", Set.of("CLIENT_ROTATE_SECRET"), false))))
                .isInstanceOfSatisfying(UamException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void aPlatformAdministratorMayGrantAnyPermission() {
        signIn("ROLE_SUPER_ADMIN");
        UamRole role = service.create(new UamRoleServiceUseCase.CreateInput(request("anything", null, Set.of("ORGANIZATION_CREATE"), false))).role();
        assertThat(role.getPermissions()).containsExactly("ORGANIZATION_CREATE");
    }

    @Test
    void thePlatformAdministratorRoleCannotBeEditedByAnyone() {
        signIn("ROLE_SUPER_ADMIN");
        UamRole superAdmin = UamRole.builder().id("a1").code("SUPER_ADMIN").tenantId(null).systemRole(true).permissions(List.of("X")).build();
        when(roles.findById("a1")).thenReturn(Optional.of(superAdmin));

        assertThatThrownBy(() -> service.update(new UamRoleServiceUseCase.UpdateInput("a1", request("super_admin", "Super", Set.of(), true))))
                .isInstanceOfSatisfying(UamException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verify(roles, never()).save(any());
    }

    @Test
    void aRoleThatPeopleStillHoldCannotBeDeleted() {
        UamRole own = UamRole.builder().id("r1").code("OPS").tenantId("acme").build();
        when(roles.findById("r1")).thenReturn(Optional.of(own));
        when(users.count(any())).thenReturn(2L);

        assertThatThrownBy(() -> service.delete(new UamRoleServiceUseCase.DeleteInput("r1")))
                .isInstanceOfSatisfying(UamException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getMessage()).contains("2 users hold").contains("OPS");
                });
        verify(roles, never()).delete(any());

        when(users.count(any())).thenReturn(0L);
        service.delete(new UamRoleServiceUseCase.DeleteInput("r1"));
        verify(roles).delete(own);
    }

    @Test
    void createRejectsBlankAndDuplicateCodesAndToleratesNoPermissions() {
        assertThatThrownBy(() -> service.create(new UamRoleServiceUseCase.CreateInput(request(" ", null, null, false))))
                .hasMessageContaining("Role code is required");
        when(roles.findByCodeIgnoreCase("DUP")).thenReturn(Optional.of(UamRole.builder().id("x").build()));
        assertThatThrownBy(() -> service.create(new UamRoleServiceUseCase.CreateInput(request("dup", null, null, false))))
                .isInstanceOfSatisfying(UamException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));

        assertThat(service.create(new UamRoleServiceUseCase.CreateInput(request("fresh", "Fresh", null, false))).role().getPermissions()).isEmpty();
    }

    @Test
    void anOrganizationAdminUpdatesOwnRoleButSystemFlagIsUntouched() {
        UamRole own = UamRole.builder().id("r1").code("OLD").tenantId("acme").systemRole(false).audit(AuditInfo.builder().build()).build();
        when(roles.findById("r1")).thenReturn(Optional.of(own));
        when(roles.findByCodeIgnoreCase("OPS")).thenReturn(Optional.of(own));

        UamRole out = service.update(new UamRoleServiceUseCase.UpdateInput("r1", request("ops", "Ops", Set.of("p"), false))).role();

        assertThat(out.getCode()).isEqualTo("OPS");
        assertThat(out.getName()).isEqualTo("Ops");
        assertThat(out.getPermissions()).containsExactly("P");
        assertThat(out.getSystemRole()).isFalse();
        assertThat(out.getAudit().getUpdatedAt()).isNotNull();
        verify(idp).syncRole(out);
    }

    @Test
    void updateRejectsAnotherRolesCode() {
        UamRole own = UamRole.builder().id("r1").tenantId("acme").build();
        when(roles.findById("r1")).thenReturn(Optional.of(own));
        when(roles.findByCodeIgnoreCase("TAKEN")).thenReturn(Optional.of(UamRole.builder().id("r2").build()));
        assertThatThrownBy(() -> service.update(new UamRoleServiceUseCase.UpdateInput("r1", request("taken", null, null, false))))
                .hasMessageContaining("already exists");
    }

    @Test
    void sharedRolesCanOnlyBeChangedOrDeletedByAPlatformAdministrator() {
        UamRole shared = UamRole.builder().id("s1").code("SHARED").tenantId(null).build();
        when(roles.findById("s1")).thenReturn(Optional.of(shared));

        assertThatThrownBy(() -> service.update(new UamRoleServiceUseCase.UpdateInput("s1", request("shared", null, null, false))))
                .isInstanceOfSatisfying(UamException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> service.delete(new UamRoleServiceUseCase.DeleteInput("s1")))
                .isInstanceOf(UamException.class);
        verify(roles, never()).delete(any());

        signIn("ROLE_SUPER_ADMIN");
        UamRole out = service.update(new UamRoleServiceUseCase.UpdateInput("s1", request("shared", null, Set.of(), true))).role();
        assertThat(out.getSystemRole()).isTrue();
        assertThat(out.getAudit()).isNull();

        // It was just made a system role, so it can no longer be deleted.
        assertThatThrownBy(() -> service.delete(new UamRoleServiceUseCase.DeleteInput("s1")))
                .isInstanceOfSatisfying(UamException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verify(roles, never()).delete(any());

        UamRole plain = UamRole.builder().id("s2").code("PLAIN").tenantId(null).systemRole(false).build();
        when(roles.findById("s2")).thenReturn(Optional.of(plain));
        service.delete(new UamRoleServiceUseCase.DeleteInput("s2"));
        verify(idp).deleteRole(plain);
        verify(roles).delete(plain);
    }

    @Test
    void anOrganizationAdminCannotMakeOrKeepASystemRole() {
        assertThatThrownBy(() -> service.create(new UamRoleServiceUseCase.CreateInput(request("ops", null, null, true))))
                .isInstanceOfSatisfying(UamException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        UamRole own = UamRole.builder().id("r1").code("OPS").tenantId("acme").systemRole(false).build();
        when(roles.findById("r1")).thenReturn(Optional.of(own));
        assertThatThrownBy(() -> service.update(new UamRoleServiceUseCase.UpdateInput("r1", request("ops", null, null, true))))
                .isInstanceOfSatisfying(UamException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(roles, never()).save(any());
    }

    @Test
    void aPlatformAdministratorMakesASharedSystemRole() {
        signIn("ROLE_SUPER_ADMIN");

        UamRole made = service.create(new UamRoleServiceUseCase.CreateInput(request("platform_ops", null, null, true))).role();

        assertThat(made.getSystemRole()).isTrue();
        assertThat(made.getTenantId()).isNull();
    }

    @Test
    void rolesWithABadCodeAreRefusedOnCreateAndOnRename() {
        for (String bad : List.of("qa role", "1ops", "ab", "x".repeat(51), "ops!", "ops-admin")) {
            assertThatThrownBy(() -> service.create(new UamRoleServiceUseCase.CreateInput(request(bad, null, null, false))))
                    .as(bad).isInstanceOf(UamException.class).hasMessageContaining("Role code");
        }
        UamRole own = UamRole.builder().id("r1").code("OLD").tenantId("acme").systemRole(false).build();
        when(roles.findById("r1")).thenReturn(Optional.of(own));
        assertThatThrownBy(() -> service.update(new UamRoleServiceUseCase.UpdateInput("r1", request("new code", null, null, false))))
                .isInstanceOf(UamException.class).hasMessageContaining("Role code");
        verify(roles, never()).save(any());
    }

    @Test
    void codeBoundariesAreAccepted() {
        assertThat(service.create(new UamRoleServiceUseCase.CreateInput(request("abc", null, null, false))).role().getCode()).isEqualTo("ABC");
        assertThat(service.create(new UamRoleServiceUseCase.CreateInput(request("a" + "b".repeat(49), null, null, false))).role().getCode())
                .hasSize(50);
        assertThat(service.create(new UamRoleServiceUseCase.CreateInput(request("ops_2", null, null, false))).role().getCode()).isEqualTo("OPS_2");
    }

    @Test
    void aRoleSavedBeforeThePatternExistedCanStillBeEditedWithItsOwnCode() {
        UamRole legacy = UamRole.builder().id("r1").code("OLD-ROLE").tenantId("acme").systemRole(false).build();
        when(roles.findById("r1")).thenReturn(Optional.of(legacy));
        when(roles.findByCodeIgnoreCase("OLD-ROLE")).thenReturn(Optional.of(legacy));

        UamRole out = service.update(new UamRoleServiceUseCase.UpdateInput("r1", request("old-role", "Renamed", null, false))).role();

        assertThat(out.getName()).isEqualTo("Renamed");
    }

    @Test
    void aSystemRoleCannotBeDeletedRenamedOrUnflagged() {
        signIn("ROLE_SUPER_ADMIN");
        UamRole superAdmin = UamRole.builder().id("a1").code("SUPER_ADMIN").tenantId(null).systemRole(true).build();
        UamRole structural = UamRole.builder().id("a2").code("DRIVER").tenantId(null).systemRole(true).build();
        UamRole unflaggedAdmin = UamRole.builder().id("a3").code("SUPER_ADMIN").tenantId(null).build();
        when(roles.findById("a1")).thenReturn(Optional.of(superAdmin));
        when(roles.findById("a2")).thenReturn(Optional.of(structural));
        when(roles.findById("a3")).thenReturn(Optional.of(unflaggedAdmin));

        for (String id : List.of("a1", "a2", "a3")) {
            assertThatThrownBy(() -> service.delete(new UamRoleServiceUseCase.DeleteInput(id)))
                    .as(id).isInstanceOfSatisfying(UamException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        }
        assertThatThrownBy(() -> service.update(new UamRoleServiceUseCase.UpdateInput("a2", request("courier", null, null, true))))
                .isInstanceOfSatisfying(UamException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verify(roles, never()).delete(any());

        UamRole kept = service.update(new UamRoleServiceUseCase.UpdateInput("a2", request("driver", "Driver", Set.of("p"), false))).role();
        assertThat(kept.getSystemRole()).isTrue();
    }

    @Test
    void anotherOrganizationsRoleIsNotFoundAndThePlatformAdminRoleIsHiddenFromOrgAdmins() {
        UamRole foreign = UamRole.builder().id("f1").code("OPS").tenantId("other").build();
        UamRole platform = UamRole.builder().id("p1").code("super_admin").tenantId(null).build();
        when(roles.findById("f1")).thenReturn(Optional.of(foreign));
        when(roles.findById("p1")).thenReturn(Optional.of(platform));
        when(roles.findById("none")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(new UamRoleServiceUseCase.GetInput("f1"))).hasMessageContaining("not found");
        assertThatThrownBy(() -> service.get(new UamRoleServiceUseCase.GetInput("p1"))).hasMessageContaining("Role not found");
        assertThatThrownBy(() -> service.get(new UamRoleServiceUseCase.GetInput("none")))
                .isInstanceOfSatisfying(UamException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));

        signIn("ROLE_SUPER_ADMIN");
        assertThat(service.get(new UamRoleServiceUseCase.GetInput("p1")).role()).isSameAs(platform);
    }

    @Test
    void listingIsScopedToTheOrganizationAndHidesThePlatformRole() {
        UamRole own = UamRole.builder().code("OPS").tenantId("acme").build();
        UamRole platform = UamRole.builder().code("SUPER_ADMIN").build();
        when(roles.findAll(any(Sort.class))).thenReturn(List.of(own, platform));
        when(roles.findVisible(eq("acme"), any(Sort.class))).thenReturn(List.of(own, platform));

        assertThat(service.list(new UamRoleServiceUseCase.ListInput()).roles()).containsExactly(own);
        assertThat(service.list(new UamRoleServiceUseCase.ListInput("acme")).roles()).containsExactly(own);

        signIn("ROLE_SUPER_ADMIN");
        assertThat(service.list(new UamRoleServiceUseCase.ListInput()).roles()).containsExactly(own, platform);
    }

    @Test
    void pagedListingAdjustsTheTotalWhenRolesAreHidden() {
        UamRole own = UamRole.builder().code("OPS").tenantId("acme").build();
        UamRole platform = UamRole.builder().code("SUPER_ADMIN").build();
        when(roles.searchVisible(eq("acme"), eq(null), eq(null), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(own, platform), org.springframework.data.domain.PageRequest.of(0, 10), 2));

        var page = service.list(new UamRoleServiceUseCase.ListPageInput(0, 10)).roles();
        assertThat(page.content()).containsExactly(own);
        assertThat(page.totalElements()).isEqualTo(1);

        when(roles.searchVisible(eq("acme"), eq("C"), eq("n"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(own), org.springframework.data.domain.PageRequest.of(0, 10), 1));
        var plain = service.list(new UamRoleServiceUseCase.ListPageInput(0, 10, "C", "n")).roles();
        assertThat(plain.content()).containsExactly(own);
        assertThat(plain.totalElements()).isEqualTo(1);
    }
}
