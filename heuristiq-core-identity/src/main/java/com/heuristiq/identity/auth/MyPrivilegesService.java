package com.heuristiq.identity.auth;

import com.heuristiq.identity.common.UamException;
import com.heuristiq.identity.dto.MyPrivilegesResponse;
import com.heuristiq.identity.permissions.Permission;
import com.heuristiq.identity.permissions.PermissionRepository;
import com.heuristiq.identity.role.UamRole;
import com.heuristiq.identity.role.repository.UamRoleRepository;
import com.heuristiq.identity.users.UamUser;
import com.heuristiq.identity.users.repository.UamUserRepository;
import org.springframework.stereotype.Service;

import com.heuristiq.identity.security.SecurityConstants;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/** Default {@link MyPrivilegesServiceUseCase}. Reads current role and permission data on every call (no snapshot). */
@Service
public class MyPrivilegesService implements MyPrivilegesServiceUseCase {

    private final UamUserRepository userRepository;
    private final UamRoleRepository roleRepository;
    private final PermissionRepository permissionRepository;

    public MyPrivilegesService(UamUserRepository userRepository, UamRoleRepository roleRepository,
                               PermissionRepository permissionRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
    }

    @Override
    public ForUserOutput forUser(ForUserInput input) {
        UamUser user = userRepository.findById(input.userId())
                .orElseThrow(() -> new UamException("User not found"));
        List<String> roles = sessionRoles(user.getRoles() == null ? List.of() : user.getRoles(), input.sessionAuthorities());

        Set<String> permissionCodes = new LinkedHashSet<>();
        for (String roleCode : roles) {
            roleRepository.findByCodeIgnoreCase(roleCode)
                    .map(UamRole::getPermissions)
                    .ifPresent(permissionCodes::addAll);
        }

        Set<String> grantedCodes = new TreeSet<>();
        Set<Integer> privilegeIds = new TreeSet<>();
        for (Permission permission : permissionRepository.findAllByCodeIn(permissionCodes)) {
            grantedCodes.add(permission.getCode());
            Integer privilegeId = privilegeIdOf(permission);
            if (privilegeId != null) {
                privilegeIds.add(privilegeId);
            }
        }
        return new ForUserOutput(new MyPrivilegesResponse(
                List.copyOf(roles), List.copyOf(grantedCodes), List.copyOf(privilegeIds)));
    }

    /**
     * The account's roles that the session's token carries: a role is kept when its authority ({@code ROLE_} + code, as the
     * JWT filter builds it) is among {@code sessionAuthorities}, ignoring case. Null authorities keep every role.
     */
    private static List<String> sessionRoles(List<String> accountRoles, Collection<String> sessionAuthorities) {
        if (sessionAuthorities == null) {
            return accountRoles;
        }
        Set<String> held = new HashSet<>();
        sessionAuthorities.forEach(authority -> held.add(authority.toUpperCase(Locale.ROOT)));
        return accountRoles.stream()
                .filter(role -> role != null && held.contains(authorityOf(role).toUpperCase(Locale.ROOT)))
                .toList();
    }

    private static String authorityOf(String role) {
        return role.startsWith(SecurityConstants.ROLE_AUTHORITY_PREFIX) ? role : SecurityConstants.ROLE_AUTHORITY_PREFIX + role;
    }

    private static Integer privilegeIdOf(Permission permission) {
        if (permission.getAttributes() == null) {
            return null;
        }
        Object version = permission.getAttributes().get(com.heuristiq.identity.common.UamCommonConstants.PERMISSION_CATALOG_VERSION_ATTRIBUTE);
        if (!(version instanceof Number catalogNumber) || catalogNumber.doubleValue() != com.heuristiq.identity.common.UamCommonConstants.PERMISSION_CATALOG_VERSION) {
            return null;
        }
        Object value = permission.getAttributes().get(MyPrivilegesConstants.PRIVILEGE_ID_ATTRIBUTE);
        return value instanceof Number number ? number.intValue() : null;
    }
}
