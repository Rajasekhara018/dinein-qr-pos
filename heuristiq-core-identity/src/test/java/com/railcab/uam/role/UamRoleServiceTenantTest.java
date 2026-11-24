package com.heuristiq.uam.role;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.heuristiq.uam.common.UamException;
import com.heuristiq.uam.dto.UpsertRoleRequest;
import com.heuristiq.uam.identity.IdentityProviderSyncService;
import com.heuristiq.uam.role.repository.UamRoleRepository;
import com.heuristiq.uam.security.TenantContext;

/** Roles belong to the organization that made them; shared (system) roles are the platform's. */
class UamRoleServiceTenantTest {
    private UamRoleRepository roles;
    private UamRoleService service;

    @BeforeEach
    void setUp() {
        roles = mock(UamRoleRepository.class);
        when(roles.save(any(UamRole.class))).thenAnswer(call -> call.getArgument(0));
        service = new UamRoleService(roles, mock(IdentityProviderSyncService.class), mock(com.heuristiq.uam.users.repository.UamUserRepository.class));
        TenantContext.set("acme");
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("admin", "x", "ROLE_ORG_ADMIN"));
    }

    @AfterEach
    void clear() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void aRoleAnOrganizationAdminCreatesBelongsToThatOrganizationAndIsNeverShared() {
        UamRole created = service.create(new UamRoleServiceUseCase.CreateInput(request("DISPATCHER", false))).role();

        assertThat(created.getTenantId()).isEqualTo("acme");
        assertThat(created.getSystemRole()).isFalse();
    }

    @Test
    void onlyAPlatformAdministratorCanMakeASharedSystemRole() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("root", "x", "ROLE_SUPER_ADMIN"));

        assertThat(service.create(new UamRoleServiceUseCase.CreateInput(request("AUDITOR", true))).role().getTenantId()).isNull();
        assertThat(service.create(new UamRoleServiceUseCase.CreateInput(request("LOCAL", false))).role().getTenantId()).isEqualTo("acme");
    }

    @Test
    void anOrganizationAdminCannotChangeOrRemoveASharedRole() {
        UamRole shared = UamRole.builder().id("r1").code("AUDITOR").build();
        when(roles.findById("r1")).thenReturn(Optional.of(shared));

        assertThatThrownBy(() -> service.update(new UamRoleServiceUseCase.UpdateInput("r1", request("AUDITOR", false))))
                .isInstanceOfSatisfying(UamException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> service.delete(new UamRoleServiceUseCase.DeleteInput("r1"))).isInstanceOf(UamException.class);
    }

    @Test
    void anOrganizationCannotOpenAnotherOrganizationsRole() {
        when(roles.findById("r2")).thenReturn(Optional.of(UamRole.builder().id("r2").code("X").tenantId("other").build()));

        assertThatThrownBy(() -> service.get(new UamRoleServiceUseCase.GetInput("r2"))).isInstanceOf(UamException.class);
    }

    @Test
    void theRoleListIsLimitedToSharedRolesAndTheSessionsOrganization() {
        when(roles.searchVisible(eq("acme"), any(), any(), any(Pageable.class))).thenReturn(new PageImpl<>(java.util.List.of()));

        service.list(new UamRoleServiceUseCase.ListPageInput(0, 10));
        service.list(new UamRoleServiceUseCase.ListInput("acme"));

        verify(roles).searchVisible(eq("acme"), any(), any(), any(Pageable.class));
        verify(roles).findVisible(eq("acme"), any());
    }

    private static UpsertRoleRequest request(String code, boolean systemRole) {
        return new UpsertRoleRequest(code, code, null, Set.of(), systemRole);
    }
}
