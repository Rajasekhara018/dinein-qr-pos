package com.heuristiq.uam.role;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.heuristiq.uam.common.UamException;
import com.heuristiq.uam.helper.AuditInfo;
import com.heuristiq.uam.identity.IdentityProviderSyncService;
import com.heuristiq.uam.role.repository.UamRoleRepository;

/** {@link UamRoleService#addPermissions}: only ever adds, keeps everything else of the role, and saves only on a change. */
class UamRoleServiceAddPermissionsTest {
    private UamRoleRepository roles;
    private IdentityProviderSyncService idp;
    private UamRoleService service;

    @BeforeEach
    void setUp() {
        roles = mock(UamRoleRepository.class);
        idp = mock(IdentityProviderSyncService.class);
        when(roles.save(any(UamRole.class))).thenAnswer(inv -> inv.getArgument(0));
        service = new UamRoleService(roles, idp, mock(com.heuristiq.uam.users.repository.UamUserRepository.class));
    }

    private UamRole existing(String tenantId, List<String> permissions) {
        UamRole role = UamRole.builder().id("r1").tenantId(tenantId).code("FRANCHISE_AGENT_RIDE").name("Agent").description("d")
                .active(false).systemRole(true).permissions(permissions)
                .audit(AuditInfo.builder().createdAt(LocalDateTime.of(2026, 1, 1, 0, 0)).updatedAt(LocalDateTime.of(2026, 1, 1, 0, 0)).build())
                .build();
        when(roles.findByCodeIgnoreCase("FRANCHISE_AGENT_RIDE")).thenReturn(Optional.of(role));
        return role;
    }

    @Test
    void anEmptyRoleGainsTheWantedPermissionsNormalizedAndKeepsItsNameFlagsAndOrganization() {
        existing("org-1", List.of());

        var out = service.addPermissions(new UamRoleServiceUseCase.AddPermissionsInput(" franchise_agent_ride ", Set.of("a_one", " B_TWO ", " ")));

        assertThat(out.added()).containsExactlyInAnyOrder("A_ONE", "B_TWO");
        assertThat(out.role().getPermissions()).containsExactlyInAnyOrder("A_ONE", "B_TWO");
        assertThat(out.role().getName()).isEqualTo("Agent");
        assertThat(out.role().getActive()).isFalse();
        assertThat(out.role().getSystemRole()).isTrue();
        assertThat(out.role().getTenantId()).isEqualTo("org-1");
        assertThat(out.role().getAudit().getUpdatedAt()).isAfter(LocalDateTime.of(2026, 1, 2, 0, 0));
        verify(idp).syncRole(out.role());
    }

    @Test
    void permissionsAlreadyThereStayAndNothingIsRemovedOrDuplicated() {
        existing(null, List.of("KEEP_ME", "A_ONE"));

        var out = service.addPermissions(new UamRoleServiceUseCase.AddPermissionsInput("FRANCHISE_AGENT_RIDE", Set.of("A_ONE", "B_TWO")));

        assertThat(out.added()).containsExactly("B_TWO");
        assertThat(out.role().getPermissions()).containsExactly("KEEP_ME", "A_ONE", "B_TWO");
    }

    @Test
    void nothingMissingMeansNothingSavedOrSynced() {
        UamRole role = existing(null, List.of("A_ONE"));

        var out = service.addPermissions(new UamRoleServiceUseCase.AddPermissionsInput("FRANCHISE_AGENT_RIDE", Set.of("a_one")));

        assertThat(out.added()).isEmpty();
        assertThat(out.role()).isSameAs(role);
        verify(roles, never()).save(any());
        verify(idp, never()).syncRole(any());
    }

    @Test
    void aRoleWithoutAPermissionListOrAuditIsHandled() {
        UamRole role = existing(null, null);
        role.setAudit(null);

        var out = service.addPermissions(new UamRoleServiceUseCase.AddPermissionsInput("FRANCHISE_AGENT_RIDE", Set.of("A_ONE")));

        assertThat(out.role().getPermissions()).containsExactly("A_ONE");
    }

    @Test
    void anUnknownRoleIsNotFoundAndABlankCodeIsRefused() {
        when(roles.findByCodeIgnoreCase("GHOST")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addPermissions(new UamRoleServiceUseCase.AddPermissionsInput("ghost", Set.of("A"))))
                .isInstanceOfSatisfying(UamException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> service.addPermissions(new UamRoleServiceUseCase.AddPermissionsInput(" ", Set.of("A"))))
                .isInstanceOf(UamException.class);
    }
}
