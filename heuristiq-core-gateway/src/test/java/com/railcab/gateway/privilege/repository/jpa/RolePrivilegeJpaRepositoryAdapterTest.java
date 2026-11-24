package com.heuristiq.gateway.privilege.repository.jpa;

import com.heuristiq.gateway.privilege.persistence.jpa.GatewayPermissionEntity;
import com.heuristiq.gateway.privilege.persistence.jpa.GatewayUamRoleEntity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RolePrivilegeJpaRepositoryAdapterTest {

    private final GatewayUamRoleJpaSpringRepository roles = mock(GatewayUamRoleJpaSpringRepository.class);
    private final GatewayPermissionJpaSpringRepository permissions = mock(GatewayPermissionJpaSpringRepository.class);
    private final RolePrivilegeJpaRepositoryAdapter adapter = new RolePrivilegeJpaRepositoryAdapter(roles, permissions);

    private static GatewayUamRoleEntity role(List<String> perms) {
        GatewayUamRoleEntity r = new GatewayUamRoleEntity();
        r.setCode("OPS");
        r.setPermissions(perms);
        return r;
    }

    private static GatewayPermissionEntity permission(Map<String, Object> attrs) {
        GatewayPermissionEntity p = new GatewayPermissionEntity();
        p.setAttributes(attrs);
        return p;
    }

    @Test
    void unknownRoleOrRoleWithoutPermissionsYieldsNothing() {
        when(roles.findByCode("NOPE")).thenReturn(Optional.empty());
        when(roles.findByCode("NULL")).thenReturn(Optional.of(role(null)));
        when(roles.findByCode("EMPTY")).thenReturn(Optional.of(role(List.of())));

        assertThat(adapter.resolvePrivilegeIds("NOPE")).isEmpty();
        assertThat(adapter.resolvePrivilegeIds("NULL")).isEmpty();
        assertThat(adapter.resolvePrivilegeIds("EMPTY")).isEmpty();
        verify(permissions, never()).findByCodeIn(anyList());
    }

    @Test
    void onlyNumericPrivilegeIdsAreReturned() {
        Map<String, Object> noAttrs = null;
        when(roles.findByCode("OPS")).thenReturn(Optional.of(role(List.of("A", "B", "C", "D"))));
        when(permissions.findByCodeIn(List.of("A", "B", "C", "D"))).thenReturn(List.of(
                permission(Map.of("privilegeId", 3, "privilegeCatalogVersion", 2)),
                permission(Map.of("privilegeId", 4.0, "privilegeCatalogVersion", 2)),
                permission(Map.of("privilegeId", "x", "privilegeCatalogVersion", 2)),
                permission(noAttrs)));

        assertThat(adapter.resolvePrivilegeIds("OPS")).containsExactlyInAnyOrder(3, 4);
    }

    @Test
    void legacyOrUnversionedNumbersCannotGrantRenumberedPrivileges() {
        when(roles.findByCode("OPS")).thenReturn(Optional.of(role(List.of("OLD", "CURRENT"))));
        when(permissions.findByCodeIn(List.of("OLD", "CURRENT"))).thenReturn(List.of(
                permission(Map.of("privilegeId", 444)),
                permission(Map.of("privilegeId", 444, "privilegeCatalogVersion", 1)),
                permission(Map.of("privilegeId", 7, "privilegeCatalogVersion", 2))));
        assertThat(adapter.resolvePrivilegeIds("OPS")).containsExactly(7);
    }
}
