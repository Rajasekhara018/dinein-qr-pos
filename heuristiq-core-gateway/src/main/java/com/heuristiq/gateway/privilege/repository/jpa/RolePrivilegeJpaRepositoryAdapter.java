package com.heuristiq.gateway.privilege.repository.jpa;

import com.heuristiq.gateway.privilege.RolePrivilegeRepository;
import com.heuristiq.gateway.common.constants.GatewayConstants;
import com.heuristiq.gateway.privilege.persistence.jpa.GatewayPermissionEntity;
import com.heuristiq.gateway.privilege.persistence.jpa.GatewayUamRoleEntity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
@ConditionalOnProperty(prefix = "railcab.gateway.persistence", name = "type", havingValue = "postgres")
class RolePrivilegeJpaRepositoryAdapter implements RolePrivilegeRepository {

    private final GatewayUamRoleJpaSpringRepository roleRepository;
    private final GatewayPermissionJpaSpringRepository permissionRepository;

    RolePrivilegeJpaRepositoryAdapter(GatewayUamRoleJpaSpringRepository roleRepository,
                                      GatewayPermissionJpaSpringRepository permissionRepository) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
    }

    @Override
    public Set<Integer> resolvePrivilegeIds(String roleCode) {
        Optional<GatewayUamRoleEntity> role = roleRepository.findByCode(roleCode);
        if (role.isEmpty() || role.get().getPermissions() == null || role.get().getPermissions().isEmpty()) {
            return Set.of();
        }

        List<GatewayPermissionEntity> permissions = permissionRepository.findByCodeIn(role.get().getPermissions());

        return permissions.stream()
                .map(this::extractPrivilegeId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private Integer extractPrivilegeId(GatewayPermissionEntity permission) {
        if (permission.getAttributes() == null) {
            return null;
        }
        Object version = permission.getAttributes().get(GatewayConstants.PERMISSION_CATALOG_VERSION_ATTRIBUTE);
        if (!(version instanceof Number catalogNumber) || catalogNumber.doubleValue() != GatewayConstants.PERMISSION_CATALOG_VERSION) {
            return null;
        }
        Object privilegeId = permission.getAttributes().get("privilegeId");
        return privilegeId instanceof Number number ? number.intValue() : null;
    }
}
